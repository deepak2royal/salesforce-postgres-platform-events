
package genericpubsub;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;

import com.google.protobuf.ByteString;
import com.salesforce.eventbus.protobuf.*;

import utility.CommonContext;
import utility.ExampleConfigurations;

/**
 * Salesforce Pub/Sub API subscriber.
 *
 * Receives Salesforce Platform Events, deserializes Avro payloads,
 * and forwards GenericRecord to the Spring Boot application.
 */
public class Subscribe extends CommonContext {

    public static int BATCH_SIZE;
    public static int MAX_RETRIES = 3;

    public static String ERROR_REPLAY_ID_VALIDATION_FAILED =
            "fetch.replayid.validation.failed";

    public static String ERROR_REPLAY_ID_INVALID =
            "fetch.replayid.corrupted";

    public static String ERROR_SERVICE_UNAVAILABLE =
            "service.unavailable";

    public static int SERVICE_UNAVAILABLE_WAIT_BEFORE_RETRY_SECONDS = 5;

    public static ExampleConfigurations exampleConfigurations;

    public static AtomicBoolean isActive = new AtomicBoolean(false);

    public static AtomicInteger retriesLeft =
            new AtomicInteger(MAX_RETRIES);

    private StreamObserver<FetchRequest> serverStream;

    private final Map<String, Schema> schemaCache =
            new ConcurrentHashMap<>();

    private final AtomicInteger receivedEvents =
            new AtomicInteger(0);

    private final StreamObserver<FetchResponse> responseStreamObserver;

    private final ReplayPreset replayPreset;

    private final ByteString customReplayId;

    private final ScheduledExecutorService retryScheduler;

    private volatile ByteString storedReplay;

    private final boolean processChangedFields;

    // Spring Boot callback
    private final Consumer<GenericRecord> eventConsumer;

    /**
     * Original Salesforce sample constructor.
     */
    public Subscribe(ExampleConfigurations exampleConfigurations) {

        super(exampleConfigurations);

        isActive.set(true);

        this.exampleConfigurations = exampleConfigurations;

        this.BATCH_SIZE =
                exampleConfigurations
                        .getNumberOfEventsToSubscribeInEachFetchRequest();

        this.responseStreamObserver =
                getDefaultResponseStreamObserver();

        this.setupTopicDetails(
                exampleConfigurations.getTopic(),
                false,
                false
        );

        this.replayPreset =
                exampleConfigurations.getReplayPreset();

        this.customReplayId =
                exampleConfigurations.getReplayId();

        this.retryScheduler =
                Executors.newScheduledThreadPool(1);

        this.processChangedFields =
                exampleConfigurations.getProcessChangedFields();

        this.eventConsumer = null;
    }

    /**
     * Original Salesforce sample constructor with custom response observer.
     */
    public Subscribe(
            ExampleConfigurations exampleConfigurations,
            StreamObserver<FetchResponse> responseStreamObserver) {

        super(exampleConfigurations);

        isActive.set(true);

        this.exampleConfigurations = exampleConfigurations;

        this.BATCH_SIZE =
                exampleConfigurations
                        .getNumberOfEventsToSubscribeInEachFetchRequest();

        this.responseStreamObserver = responseStreamObserver;

        this.setupTopicDetails(
                exampleConfigurations.getTopic(),
                false,
                false
        );

        this.replayPreset =
                exampleConfigurations.getReplayPreset();

        this.customReplayId =
                exampleConfigurations.getReplayId();

        this.retryScheduler =
                Executors.newScheduledThreadPool(1);

        this.processChangedFields =
                exampleConfigurations.getProcessChangedFields();

        this.eventConsumer = null;
    }

    /**
     * Spring Boot integration constructor.
     *
     * The Consumer receives each deserialized Salesforce event.
     */
    public Subscribe(
            ExampleConfigurations exampleConfigurations,
            Consumer<GenericRecord> eventConsumer) {

        super(exampleConfigurations);

        if (eventConsumer == null) {
            throw new IllegalArgumentException(
                    "Event consumer must not be null"
            );
        }

        isActive.set(true);

        this.exampleConfigurations = exampleConfigurations;

        this.BATCH_SIZE =
                exampleConfigurations
                        .getNumberOfEventsToSubscribeInEachFetchRequest();

        this.responseStreamObserver =
                getDefaultResponseStreamObserver();

        this.setupTopicDetails(
                exampleConfigurations.getTopic(),
                false,
                false
        );

        this.replayPreset =
                exampleConfigurations.getReplayPreset();

        this.customReplayId =
                exampleConfigurations.getReplayId();

        this.retryScheduler =
                Executors.newScheduledThreadPool(1);

        this.processChangedFields =
                exampleConfigurations.getProcessChangedFields();

        this.eventConsumer = eventConsumer;
    }

    /**
     * Starts the Salesforce subscription.
     */
    public void startSubscription() {

        logger.info(
                "Subscription started for topic: " + busTopicName + "."
        );

        fetch(
                BATCH_SIZE,
                busTopicName,
                replayPreset,
                customReplayId
        );

        while (isActive.get()) {

            waitInMillis(5000);

            logger.info(
                    "Subscription Active. Received a total of "
                            + receivedEvents.get()
                            + " events."
            );
        }
    }

    /**
     * Sends FetchRequest to Salesforce.
     */
    public void fetch(
            int providedBatchSize,
            String providedTopicName,
            ReplayPreset providedReplayPreset,
            ByteString providedReplayId) {

        serverStream =
                asyncStub.subscribe(this.responseStreamObserver);

        FetchRequest.Builder fetchRequestBuilder =
                FetchRequest.newBuilder()
                        .setNumRequested(providedBatchSize)
                        .setTopicName(providedTopicName)
                        .setReplayPreset(providedReplayPreset);

        if (providedReplayPreset == ReplayPreset.CUSTOM) {

            logger.info(
                    "Subscription Replay Preset is CUSTOM."
            );

            fetchRequestBuilder.setReplayId(providedReplayId);
        }

        serverStream.onNext(fetchRequestBuilder.build());
    }

    /**
     * Calculates exponential backoff delay.
     */
    public long getBackoffWaitTime() {

        return (long) (
                Math.pow(
                        2,
                        MAX_RETRIES - retriesLeft.get()
                ) * 1000
        );
    }

    /**
     * Wait helper.
     */
    public void waitInMillis(long duration) {

        synchronized (this) {

            try {

                this.wait(duration);

            } catch (InterruptedException e) {

                Thread.currentThread().interrupt();

                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Handles Salesforce FetchResponse messages.
     */
    private StreamObserver<FetchResponse>
    getDefaultResponseStreamObserver() {

        return new StreamObserver<FetchResponse>() {

            @Override
            public void onNext(FetchResponse fetchResponse) {

                logger.info(
                        "Received batch of "
                                + fetchResponse.getEventsList().size()
                                + " events"
                );

                logger.info(
                        "RPC ID: " + fetchResponse.getRpcId()
                );

                for (ConsumerEvent ce :
                        fetchResponse.getEventsList()) {

                    try {

                        processEvent(ce);

                    } catch (Exception e) {

                        logger.error(
                                "Error processing Salesforce event",
                                e
                        );
                    }

                    receivedEvents.incrementAndGet();
                }

                storedReplay =
                        fetchResponse.getLatestReplayId();

                if (retriesLeft.get() != MAX_RETRIES) {
                    retriesLeft.set(MAX_RETRIES);
                }

                if (fetchResponse.getPendingNumRequested() == 0) {

                    fetchMore(BATCH_SIZE);
                }
            }

            @Override
            public void onError(Throwable t) {

                logger.error(
                        "Error during Salesforce subscription",
                        t
                );

                logger.info(
                        "Retries remaining: " + retriesLeft.get()
                );

                if (retriesLeft.get() == 0) {

                    logger.error(
                            "Exhausted all retries. Closing subscription."
                    );

                    isActive.set(false);

                    return;
                }

                retriesLeft.decrementAndGet();

                Metadata trailers = null;

                if (t instanceof StatusRuntimeException) {

                    trailers =
                            ((StatusRuntimeException) t).getTrailers();
                }

                String errorCode = null;

                if (trailers != null) {

                    Metadata.Key<String> errorKey =
                            Metadata.Key.of(
                                    "error-code",
                                    Metadata.ASCII_STRING_MARSHALLER
                            );

                    errorCode = trailers.get(errorKey);
                }

                if (serverStream != null) {

                    serverStream.onCompleted();
                }

                ReplayPreset retryReplayPreset =
                        ReplayPreset.LATEST;

                ByteString retryReplayId = null;

                long retryDelay = getBackoffWaitTime();

                if (errorCode != null && !errorCode.isEmpty()) {

                    if (
                            errorCode.contains(
                                    ERROR_REPLAY_ID_VALIDATION_FAILED
                            )
                            ||
                            errorCode.contains(
                                    ERROR_REPLAY_ID_INVALID
                            )
                    ) {

                        logger.info(
                                "Invalid Replay ID. Retrying with EARLIEST."
                        );

                        retryReplayPreset = ReplayPreset.EARLIEST;

                    } else if (
                            errorCode.contains(
                                    ERROR_SERVICE_UNAVAILABLE
                            )
                    ) {

                        logger.info(
                                "Salesforce service unavailable. Retrying."
                        );

                        retryDelay =
                                SERVICE_UNAVAILABLE_WAIT_BEFORE_RETRY_SECONDS
                                        * 1000;

                    } else {

                        if (storedReplay != null) {

                            logger.info(
                                    "Retrying using stored Replay ID."
                            );

                            retryReplayPreset = ReplayPreset.CUSTOM;

                            retryReplayId = getStoredReplay();

                        } else {

                            logger.info(
                                    "Retrying using LATEST Replay."
                            );
                        }
                    }

                } else {

                    logger.info(
                            "Unknown error. Retrying using LATEST Replay."
                    );
                }

                logger.info(
                        "Retrying in " + retryDelay + " ms."
                );

                retryScheduler.schedule(
                        new RetryRequestSender(
                                retryReplayPreset,
                                retryReplayId
                        ),
                        retryDelay,
                        TimeUnit.MILLISECONDS
                );
            }

            @Override
            public void onCompleted() {

                logger.info(
                        "Salesforce subscription completed."
                );

                isActive.set(false);
            }
        };
    }

    /**
     * Retry request sender.
     */
    private class RetryRequestSender implements Runnable {

        private final ReplayPreset retryReplayPreset;

        private final ByteString retryReplayId;

        public RetryRequestSender(
                ReplayPreset replayPreset,
                ByteString replayId) {

            this.retryReplayPreset = replayPreset;
            this.retryReplayId = replayId;
        }

        @Override
        public void run() {

            if (!isActive.get()) {
                return;
            }

            fetch(
                    BATCH_SIZE,
                    busTopicName,
                    retryReplayPreset,
                    retryReplayId
            );

            logger.info("Retry FetchRequest sent.");
        }
    }

    /**
     * Deserializes the Salesforce event and forwards it to Spring Boot.
     */
    private void processEvent(ConsumerEvent ce) throws IOException {

        Schema writerSchema =
                getSchema(ce.getEvent().getSchemaId());

        this.storedReplay = ce.getReplayId();

        GenericRecord record =
                deserialize(
                        writerSchema,
                        ce.getEvent().getPayload()
                );

        logger.info(
                "Received event payload: "
                        + record
                        + " with schema name: "
                        + writerSchema.getName()
        );

        // Forward decoded Avro event to Spring Boot.
        if (eventConsumer != null) {

            eventConsumer.accept(record);
        }

        if (processChangedFields) {

            processAndPrintBitmapFields(
                    writerSchema,
                    record,
                    "changedFields"
            );
        }
    }

    /**
     * Gets Salesforce event schema.
     */
    public Schema getSchema(String schemaId) {

        return schemaCache.computeIfAbsent(
                schemaId,
                id -> {

                    SchemaRequest request =
                            SchemaRequest.newBuilder()
                                    .setSchemaId(id)
                                    .build();

                    String schemaJson =
                            blockingStub.getSchema(request)
                                    .getSchemaJson();

                    return new Schema.Parser().parse(schemaJson);
                }
        );
    }

    /**
     * Requests more events.
     */
    public void fetchMore(int numEvents) {

        if (serverStream == null) {
            return;
        }

        FetchRequest fetchRequest =
                FetchRequest.newBuilder()
                        .setTopicName(this.busTopicName)
                        .setNumRequested(numEvents)
                        .build();

        serverStream.onNext(fetchRequest);
    }

    public AtomicInteger getReceivedEvents() {
        return receivedEvents;
    }

    public void updateReceivedEvents(int delta) {
        receivedEvents.addAndGet(delta);
    }

    public int getBatchSize() {
        return BATCH_SIZE;
    }

    public ByteString getStoredReplay() {
        return storedReplay;
    }

    public void setStoredReplay(ByteString storedReplay) {
        this.storedReplay = storedReplay;
    }

    /**
     * Closes Salesforce subscription resources.
     */
    @Override
    public synchronized void close() {

        isActive.set(false);

        try {

            if (serverStream != null) {
                serverStream.onCompleted();
            }

            if (retryScheduler != null) {
                retryScheduler.shutdownNow();
            }

        } catch (Exception e) {

            logger.error(
                    "Error closing Salesforce subscriber",
                    e
            );
        }

        super.close();
    }

    /**
     * Standalone Salesforce sample entry point.
     */
    public static void main(String args[]) throws IOException {

        ExampleConfigurations configuration =
                new ExampleConfigurations("arguments.yaml");

        try (
                Subscribe subscribe =
                        new Subscribe(configuration)
        ) {

            subscribe.startSubscription();

        } catch (Exception e) {

            printStatusRuntimeException(
                    "Error during Subscribe",
                    e
            );
        }
    }
}
