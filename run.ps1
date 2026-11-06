param(
    [switch]$EnableSalesforce
)

$ErrorActionPreference = "Stop"

Write-Host "Checking Java and Maven..."
java -version
mvn -version

if ($EnableSalesforce) {
    if (-not $env:SALESFORCE_INSTANCE_URL -or
        -not $env:SALESFORCE_TENANT_ID -or
        -not $env:SALESFORCE_ACCESS_TOKEN) {
        throw "Set SALESFORCE_INSTANCE_URL, SALESFORCE_TENANT_ID and SALESFORCE_ACCESS_TOKEN before using -EnableSalesforce."
    }
    $env:SALESFORCE_PUBSUB_ENABLED = "true"
} else {
    if (-not $env:SALESFORCE_PUBSUB_ENABLED) {
        $env:SALESFORCE_PUBSUB_ENABLED = "false"
    }
}

Write-Host "Building unified Salesforce + Spring Boot project..."
mvn clean compile
if ($LASTEXITCODE -ne 0) { throw "Maven compilation failed." }

Write-Host "Starting Spring Boot application..."
mvn spring-boot:run
