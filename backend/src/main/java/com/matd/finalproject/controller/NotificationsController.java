package com.matd.finalproject.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Controller;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import java.time.Duration;
import java.util.Collections;
import java.util.Properties;

import org.apache.kafka.clients.consumer.KafkaConsumer;

@Controller
public class NotificationsController {
    private static final Logger logger = LoggerFactory.getLogger(NotificationsController.class);

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Scheduled(fixedRate = 5000)
    public void sendAlerts() {
        Properties props = new Properties();
        props.put("bootstrap.servers", System.getenv("KAFKA_BOOTSTRAP_SERVERS"));
        props.put("group.id", "temperature-alerts-websocket-consumer");
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("enable.auto.commit", "true");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList("temperature-alerts"));

            // Poll records within the scheduled interval
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500)); // Poll for 500ms

            records.forEach(record -> {
                logger.info("Received Temperature Alert: {}", record.value());
                // Send the record to WebSocket subscribers
                messagingTemplate.convertAndSend("/topic/alerts", record.value());
            });
        } catch (Exception e) {
            logger.error("Error while sending alerts to WebSocket clients", e);
        }
    }
}