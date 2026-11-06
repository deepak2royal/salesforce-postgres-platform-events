package com.example.odata.subscriber;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.annotation.PreDestroy;

import org.apache.avro.generic.GenericRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.example.odata.consumer.ContactEventConsumer;
import com.example.odata.dto.ContactEventDTO;
import com.google.protobuf.ByteString;
import com.salesforce.eventbus.protobuf.ReplayPreset;

import genericpubsub.Subscribe;
import utility.ExampleConfigurations;

/**
 * Spring-managed adapter between Salesforce Pub/Sub API and the
 * application's ContactEventConsumer.
 *
 * Enable with SALESFORCE_PUBSUB_ENABLED=true and configure the
 * Salesforce instance URL, organization ID and OAuth access token.
 */
@Component
@ConditionalOnProperty(
        prefix = "salesforce.pubsub",
        name = "enabled",
        havingValue = "true"
)
public class ContactEventSubscriber {

    private static final Logger log =
            LoggerFactory.getLogger(ContactEventSubscriber.class);

    private final ContactEventConsumer eventConsumer;
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();
    private final AtomicBoolean started = new AtomicBoolean(false);

    private volatile Subscribe subscribe;

    @Value("${salesforce.pubsub.instance-url}")
    private String instanceUrl;

    @Value("${salesforce.pubsub.tenant-id}")
    private String tenantId;

    @Value("${salesforce.pubsub.access-token}")
    private String accessToken;

    @Value("${salesforce.pubsub.host:api.pubsub.salesforce.com}")
    private String pubsubHost;

    @Value("${salesforce.pubsub.port:7443}")
    private Integer pubsubPort;

    @Value("${salesforce.pubsub.topic:/event/Contact_Updated__e}")
    private String topic;

    @Value("${salesforce.pubsub.batch-size:5}")
    private Integer batchSize;

    public ContactEventSubscriber(ContactEventConsumer eventConsumer) {
        this.eventConsumer = eventConsumer;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void startSubscriber() {
        if (!started.compareAndSet(false, true)) {
            return;
        }

        if (isBlank(instanceUrl) || isBlank(tenantId) || isBlank(accessToken)) {
            started.set(false);
            throw new IllegalStateException(
                    "Salesforce Pub/Sub is enabled but instance-url, tenant-id "
                    + "or access-token is missing. Configure the SALESFORCE_* "
                    + "environment variables."
            );
        }

        log.info("Initializing Salesforce Pub/Sub subscriber for {}", topic);

        try {
            ExampleConfigurations configuration =
                    new ExampleConfigurations(
                            null,               // username: not used with access token
                            null,               // password: not used with access token
                            instanceUrl,        // Salesforce instance_url from OAuth response
                            tenantId,           // Salesforce Organization ID
                            accessToken,        // OAuth access token
                            pubsubHost,
                            pubsubPort,
                            topic,
                            5,                  // publish batch size (unused by subscriber)
                            false,
                            batchSize,
                            false,              // process changedFields bitmap
                            false,              // TLS enabled; do not use plaintext
                            true,               // provided instance URL
                            ReplayPreset.LATEST,
                            (ByteString) null,
                            null,
                            null
                    );

            subscribe = new Subscribe(configuration, this::processEvent);

            executor.submit(() -> {
                try {
                    log.info("Starting Salesforce subscription to {}", topic);
                    subscribe.startSubscription();
                } catch (Exception ex) {
                    log.error("Salesforce Pub/Sub subscription stopped unexpectedly", ex);
                }
            });
        } catch (Exception ex) {
            started.set(false);
            log.error("Could not initialize Salesforce Pub/Sub subscriber", ex);
            throw new IllegalStateException(
                    "Could not initialize Salesforce Pub/Sub subscriber", ex
            );
        }
    }

    /**
     * Invoked by genericpubsub.Subscribe after Avro payload deserialization.
     */
    public void processEvent(GenericRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("Salesforce event record must not be null");
        }

        ContactEventDTO event = new ContactEventDTO();
        event.setContactId(readString(record, "Contact_Id__c"));
        event.setFirstName(readString(record, "First_Name__c"));
        event.setLastName(readString(record, "Last_Name__c"));
        event.setEmail(readString(record, "Email__c"));
        event.setPhone(readString(record, "Phone_Number__c"));
        event.setOperation(readString(record, "Operation__c"));
        event.setSourceSystem(readString(record, "Source_System__c"));

        if (isBlank(event.getContactId())) {
            throw new IllegalArgumentException(
                    "Contact_Id__c is missing from Salesforce event"
            );
        }

        log.info("Received Contact event: contactId={}, operation={}",
                event.getContactId(), event.getOperation());

        // This call returns after the service/repository transaction completes.
        eventConsumer.handleContactEvent(event);

        log.info("Contact event processed: {}", event.getContactId());
    }

    private String readString(GenericRecord record, String fieldName) {
        if (record.getSchema().getField(fieldName) == null) {
            log.warn("Field {} is not present in schema {}", fieldName,
                    record.getSchema().getName());
            return null;
        }

        Object value = record.get(fieldName);
        return value == null ? null : value.toString();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @PreDestroy
    public void stopSubscriber() {
        log.info("Stopping Salesforce Pub/Sub subscriber");

        if (subscribe != null) {
            Subscribe.isActive.set(false);
            subscribe.close();
        }

        executor.shutdownNow();
    }
}
