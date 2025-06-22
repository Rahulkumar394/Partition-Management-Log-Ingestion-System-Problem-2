// FILE: log-ingestion-system/src/main/java/com/log/producer/LogProducer.java
package com.log.producer;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Random;

@Component
@Profile("producer")
public class LogProducer {
    private static final Logger log = LoggerFactory.getLogger(LogProducer.class);

    @Value("${kafka.topic.name}")
    private String topicName;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Random random = new Random();
    private final MeterRegistry meterRegistry;

    private Counter messagesFailedCounter;
    private Timer sendTimer;

    public LogProducer(KafkaTemplate<String, String> kafkaTemplate, MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void initMetrics() {
        messagesFailedCounter = Counter.builder("kafka.producer.messages.failed")
                                       .description("Total number of failed messages")
                                       .register(meterRegistry);

        sendTimer = Timer.builder("kafka.producer.send.duration")
                         .description("Time taken to send messages to Kafka")
                         .register(meterRegistry);
    }

    @Scheduled(fixedRate = 100) // 10 messages per second to generate enough data
    public void produceLog() {
        Timer.Sample sample = Timer.start(meterRegistry);
        String appName = "app-" + String.format("%03d", random.nextInt(100));
        try {
            String logMessage = createLogMessage(appName);

            // ✅ FIX: Custom metric to track sent messages per appName
            meterRegistry.counter("kafka.producer.messages.sent.by_app", "appName", appName).increment();

            kafkaTemplate.send(topicName, appName, logMessage).whenComplete((result, ex) -> {
                sample.stop(sendTimer);
                if (ex == null) {
                    int partition = result.getRecordMetadata().partition();
                    log.info("✅ [Sent] Partition: {} | Key: {} | Message: {}", partition, appName, logMessage.substring(0, 50) + "...");
                } else {
                    messagesFailedCounter.increment();
                    log.error("❌ [Failed] Key: {} | Error: {}", appName, ex.getMessage());
                }
            });

        } catch (Exception e) {
            sample.stop(sendTimer);
            messagesFailedCounter.increment();
            log.error("❌ [Exception] Error producing log for app: {}", appName, e);
        }
    }

    private String createLogMessage(String appName) {
        String[] logLevels = {"INFO", "WARN", "ERROR", "DEBUG"};
        String[] actions = {"UserLogin", "PaymentProcessed", "DataSync", "CacheUpdate", "ApiCall"};
        String level = logLevels[random.nextInt(logLevels.length)];
        String action = actions[random.nextInt(actions.length)];
        return String.format("[%s] %s - %s executed at %s by %s", level, action, action, Instant.now(), appName);
    }
}