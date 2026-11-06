# Unified Salesforce Platform Events → Spring Boot → PostgreSQL project

This folder combines the two uploaded projects into **one Maven/Spring Boot project**.
There is one application entry point (`OdataApplication`). The Salesforce Pub/Sub API
client is compiled into the same application; there is no second Maven build and no
local Maven install of a separate Pub/Sub JAR.

## Architecture

```text
Salesforce Contact
    |
    v
Apex Trigger
    |
    v
Contact_Updated__e Platform Event
    |
    v
Salesforce Pub/Sub API (gRPC)
    |
    v
genericpubsub.Subscribe
    |
    | Avro GenericRecord callback
    v
ContactEventSubscriber
    |
    v
ContactEventConsumer
    |
    v
ContactService / ContactRepository
    |
    v
PostgreSQL contacts table
```

The existing REST/OData and OAuth2 components from the Spring Boot project remain in
this same application.

## Project layout

```text
salesforce-postgres-platform-events/
├── pom.xml
├── db/
│   └── schema.sql
├── src/
│   └── main/
│       ├── java/
│       │   ├── genericpubsub/       # Salesforce Pub/Sub sample client
│       │   ├── utility/             # Salesforce gRPC/auth/Avro helpers
│       │   └── com/example/odata/   # Existing Spring Boot application
│       ├── proto/
│       │   └── pubsub_api.proto     # Generates Salesforce protobuf/gRPC classes
│       └── resources/
│           └── application.properties
└── README.md
```

## Requirements

- JDK 21
- Maven 3.9+
- PostgreSQL 14+
- Salesforce org with the `Contact_Updated__e` Platform Event
- OAuth access token, Salesforce instance URL, and Salesforce Organization ID

## 1. Create the PostgreSQL database

In pgAdmin or psql, create the database:

```sql
CREATE DATABASE ecommerce_db;
```

Connect to `ecommerce_db` and execute:

```text
db/schema.sql
```

This script creates the existing `customer_orders` demo table and the `contacts`
table used by the Salesforce event consumer.

The default local database configuration in `application.properties` is:
- URL: `jdbc:postgresql://localhost:5432/ecommerce_db`
- Username: `postgres`
- Password: set `DB_PASSWORD` in your environment

Set the connection values with environment variables. Example PowerShell:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/ecommerce_db"
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "YOUR_POSTGRES_PASSWORD"
```

The application uses `spring.jpa.hibernate.ddl-auto=validate`; it will not create
missing tables automatically. Run `db/schema.sql` first.

## 2. Configure Salesforce Platform Event access

The subscriber is **disabled by default** so that the REST/OData application can
start and be tested without Salesforce credentials. `SALESFORCE_ACCESS_TOKEN`,
Salesforce organization details, and client secrets must be supplied through local
environment variables; never commit them.

In PowerShell, configure the OAuth values. Use the `instance_url` returned by your
Salesforce OAuth token response, not the generic login URL. `TENANT_ID` is the
Salesforce Organization ID (starts with `00D`).

```powershell
$env:SALESFORCE_PUBSUB_ENABLED = "true"
$env:SALESFORCE_INSTANCE_URL = "https://YOUR-MY-DOMAIN.my.salesforce.com"
$env:SALESFORCE_TENANT_ID = "00Dxxxxxxxxxxxxxxx"
$env:SALESFORCE_ACCESS_TOKEN = "YOUR_CURRENT_OAUTH_ACCESS_TOKEN"
$env:SALESFORCE_PUBSUB_HOST = "api.pubsub.salesforce.com"
$env:SALESFORCE_PUBSUB_PORT = "7443"
$env:SALESFORCE_PUBSUB_TOPIC = "/event/Contact_Updated__e"
$env:SALESFORCE_PUBSUB_BATCH_SIZE = "5"
```

Do not commit access tokens or put them in source control. Access tokens expire.
This project currently accepts a supplied access token; automatic OAuth refresh is
not implemented. For a long-running production deployment, implement a supported
token refresh flow and secure secret storage.

The subscriber uses `ReplayPreset.LATEST` at startup. It does not persist replay IDs
to PostgreSQL or durable storage. Plan and test replay/recovery behavior before
production use.

### Platform Event fields expected by this project

The Avro event schema must include these exact API names:

- `Contact_Id__c`
- `First_Name__c`
- `Last_Name__c`
- `Email__c`
- `Phone__c`
- `Operation__c`
- `Source_System__c`

The subscriber maps those fields into `ContactEventDTO`. `Operation__c` should be
`INSERT`, `UPDATE`, or `DELETE`; the service deletes the local row for `DELETE` and
inserts/updates it for other operations.

## 3. Build the one combined project

Open PowerShell in the project root (the folder containing `pom.xml`):

```powershell
mvn clean
mvn compile
```

The Maven protobuf plugin generates Salesforce gRPC/protobuf Java classes from
`src/main/proto/pubsub_api.proto` during the build. Do not manually copy generated
Java files into `src/main/java`.

Package the application:

```powershell
mvn package -DskipTests
```

## 4. Run the application

With the Salesforce subscriber disabled (default):

```powershell
mvn spring-boot:run
```

With Salesforce subscriber enabled, first set the environment variables in step 2
in the same PowerShell window, then run:

```powershell
mvn spring-boot:run
```

You should see the Spring Boot web application start. When enabled, logs should
also indicate that the Salesforce subscriber is initializing and subscribing to
`/event/Contact_Updated__e`.

The web application and subscriber run in the same JVM:
- Spring Boot starts the REST/OData/OAuth endpoints.
- The Pub/Sub subscriber runs on a background executor.
- Incoming event payloads are deserialized by the Salesforce sample client and
  passed to the Spring-managed consumer.
- The consumer invokes `ContactService`, which writes to PostgreSQL.

## 5. Verify the database

After creating or updating a Contact in Salesforce and confirming that the Apex
trigger publishes the Platform Event, run:

```sql
SELECT *
FROM contacts
ORDER BY updated_at DESC;
```

After a Salesforce delete event:

```sql
SELECT *
FROM contacts
WHERE salesforce_contact_id = 'YOUR_SALESFORCE_CONTACT_ID';
```

The row should no longer exist if the event was received and processed.

## 6. Run the existing REST/OData APIs

The existing application uses port 8080 by default. Existing OAuth2 and API
instructions remain in the original application README material; see the controllers
under `src/main/java/com/example/odata/controller`.

## Troubleshooting

### `package genericpubsub does not exist` or `package utility does not exist`

Confirm these folders are present under `src/main/java` in this unified project:

```text
src/main/java/genericpubsub
src/main/java/utility
```

### `package com.salesforce.eventbus.protobuf does not exist`

Run `mvn clean compile`. The protobuf plugin should generate those classes from
`src/main/proto/pubsub_api.proto`.

### Salesforce `UNAUTHENTICATED`

Verify all three values:
- `SALESFORCE_ACCESS_TOKEN`: current OAuth access token
- `SALESFORCE_INSTANCE_URL`: exact `instance_url` from the OAuth response
- `SALESFORCE_TENANT_ID`: Salesforce Organization ID

Also verify the user/app has Pub/Sub API access and permission to subscribe to the
Platform Event.

### PostgreSQL connection or schema validation errors

Check the `DB_*` variables and ensure both `customer_orders` and `contacts` tables
exist in `ecommerce_db`.

### Port 8080 is already in use

Stop the existing process or set `server.port=8081` in `application.properties`.

## Important production considerations

This is an integration/demo starting point, not a production-hardened event
processing platform. Before production:
- implement OAuth access-token refresh;
- persist replay IDs and define replay/recovery policy;
- add durable retry/dead-letter handling for failed database writes;
- make event processing idempotent;
- secure database credentials and OAuth secrets;
- protect the RSA private key directory `.keys/`;
- monitor subscriber health and reconnect behavior.

The Salesforce sample's subscription retry logic is retained as a starting point.
A caught downstream processing failure is not, by itself, a durable dead-letter
or replay guarantee.
