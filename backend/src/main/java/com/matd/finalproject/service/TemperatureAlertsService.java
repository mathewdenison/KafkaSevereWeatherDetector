package com.matd.finalproject.service;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

@Service
public class TemperatureAlertsService {

    private static final String TEMPERATURE_ALERTS_TOPIC = "temperature-alerts";
    private final KafkaConsumer<String, String> consumer;

    public TemperatureAlertsService() {
        Properties props = new Properties();
        props.put("bootstrap.servers", System.getenv("KAFKA_BOOTSTRAP_SERVERS"));
        props.put("group.id", "temperature-alerts-ui-consumer");
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("enable.auto.commit", "true");

        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList(TEMPERATURE_ALERTS_TOPIC));
    }

    public String consumeAlerts() {
        StringBuilder messages = new StringBuilder();

        // Poll Kafka for new records
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
        for (ConsumerRecord<String, String> record : records) {
            messages.append(record.value()).append("\n");
        }

        return messages.toString();
    }
}