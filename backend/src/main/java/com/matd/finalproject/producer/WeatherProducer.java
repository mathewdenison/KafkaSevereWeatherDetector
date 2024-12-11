package com.matd.finalproject.producer;

import com.matd.finalproject.service.CoordinationService;
import com.matd.finalproject.spark.ClimateProcessingWithSpark;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.json.JSONObject;

@Component
public class WeatherProducer extends BaseProducer {

    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final Logger logger = LoggerFactory.getLogger(WeatherProducer.class);
    private CoordinationService service = CoordinationService.getInstance();

    public void start() {
        logger.info("Starting Weather Producer...");
        scheduler = Executors.newScheduledThreadPool(1);
        produce();
    }

    public void stop() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    public void produce() {
        Properties props = new Properties();
        String bootstrapServers = System.getenv("KAFKA_BOOTSTRAP_SERVERS");
        if (bootstrapServers == null || bootstrapServers.isEmpty()) {
            bootstrapServers = "kafka:9092"; // Default to Docker service name if not set
        }
        logger.info("Kafka Weather producer configured with servers: {}", bootstrapServers);

        props.put("max.request.size", "2097152");
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        KafkaProducer<String, String> producer = new KafkaProducer<>(props);
        String topic = "weather-data";

        service.markWeatherReady();

        scheduler.scheduleAtFixedRate(() -> {
            logger.info("Starting new scheduled request for weather data");
            service.coordinateAccess(() -> {
                logger.info("Entered coordinate access block in WeatherProducer.");
                service.waitForBothReady();

                logger.info("WeatherProducer: Both producers are ready, proceeding to fetch coordinate...");
                try {
                    // Fetch one coordinate at a time
                    BigDecimal[] coordinate = CoordinationService.getInstance().getNextCoordinate();
                    BigDecimal lat = coordinate[0];
                    BigDecimal lon = coordinate[1];

                    // Request weather data for this coordinate
                    logger.info("Requesting weather data for lat: {}, lon: {}", lat, lon);

                    String dailyParameters = "temperature_2m_mean";
                    String timezone = "auto";

                    String urlString = String.format(
                            "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f&daily=%s&timezone=%s",
                            lat, lon, dailyParameters, timezone
                    );

                    URL url = new URL(urlString);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("GET");

                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder content = new StringBuilder();
                    String inputLine;

                    while ((inputLine = in.readLine()) != null) {
                        content.append(inputLine);
                    }

                    in.close();
                    conn.disconnect();

                    // Extracting latitude and longitude from the content JSON
                    UUID uuid = service.getSharedUUID("WeatherProducer");


                    producer.send(new ProducerRecord<>(topic, uuid.toString(), content.toString()), (RecordMetadata metadata, Exception exception) -> {
                        if (exception != null) {
                            logger.error("Failed to send weather data to Kafka for uuid" + uuid + "\n With exception: " + exception);
                        } else {
                            logger.info("Weather data successfully sent to Kafka topic {} at offset {}", metadata.topic(), metadata.offset());
                        }
                    });
                } catch (Exception e) {
                    logger.error("Error during weather data request cycle.", e);
                } finally {
                    service.markWeatherReady();
                    service.notifyUUIDConsumed("WeatherProducer");
                }
            });
        }, 0, 1, TimeUnit.MINUTES); // Schedule every 1 minute interval
    }
}