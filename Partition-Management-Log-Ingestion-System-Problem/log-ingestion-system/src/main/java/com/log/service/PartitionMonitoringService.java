// FILE: log-ingestion-system/src/main/java/com/log/service/PartitionMonitoringService.java
package com.log.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Profile("consumer")
public class PartitionMonitoringService {

    private static final Logger log = LoggerFactory.getLogger(PartitionMonitoringService.class);
    private static final String CONSUMER_GROUP = "log-consumer-group";

    @Value("${kafka.topic.name}")
    private String topicName;

    // ✅ FIX: Using centralized partition count
    @Value("${kafka.topic.partitions}")
    private int partitionCount;

    private final AdminClient adminClient;
    private final MeterRegistry meterRegistry;
    private final Map<Integer, Double> partitionLagMap = new ConcurrentHashMap<>();

    @Autowired
    public PartitionMonitoringService(KafkaAdmin kafkaAdmin, MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        Properties props = new Properties();
        props.putAll(kafkaAdmin.getConfigurationProperties());
        this.adminClient = AdminClient.create(props);
    }

    @PostConstruct
    public void registerGauges() {
        for (int i = 0; i < partitionCount; i++) {
            final int partition = i;
            // ✅ FIX: Using a more standard metric name 'kafka_consumer_lag'
            Gauge.builder("kafka_consumer_lag", () -> getPartitionLag(partition))
                .description("Consumer lag for a specific partition")
                .tag("partition", String.valueOf(partition))
                .tag("topic", topicName)
                .tag("group", CONSUMER_GROUP)
                .register(meterRegistry);
        }
    }

    @Scheduled(fixedRate = 30000)
    public void monitorConsumerLag() {
        // ... (This method is fine, no major change needed)
    }

    public Double getPartitionLag(int partition) {
        return partitionLagMap.getOrDefault(partition, 0.0);
    }

    // ✅ FIX: Improved logging for automated partition balance validation
    @Scheduled(fixedRate = 60000) // Har 1 minute
    public void logPartitionDistribution() {
        try {
            Map<TopicPartition, OffsetAndMetadata> offsets =
                    adminClient.listConsumerGroupOffsets(CONSUMER_GROUP).partitionsToOffsetAndMetadata().get();

            log.info("📊 === Partition Balance Validation Summary ===");
            long activePartitions = offsets.keySet().stream()
                .filter(tp -> tp.topic().equals(topicName))
                .count();

            log.info("✅ Consumers have received messages from {} out of {} partitions.", activePartitions, partitionCount);

            if (activePartitions > 0 && activePartitions < partitionCount * 0.8) { // Agar 80% se kam active hain
                log.warn("⚠️ Partition distribution might be skewed! Only {} partitions are active.", activePartitions);
            }
            log.info("==========================================");

        } catch (Exception e) {
            log.error("Error logging partition distribution", e);
        }
    }
}