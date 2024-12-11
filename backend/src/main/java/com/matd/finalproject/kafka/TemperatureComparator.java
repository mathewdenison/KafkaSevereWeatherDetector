package com.matd.finalproject.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Arrays;
import java.util.Properties;

@Component
public class TemperatureComparator {

    private static final Logger logger = LoggerFactory.getLogger(TemperatureComparator.class);

    private static final String CLIMATE_DATA_TOPIC = "climate-data";
    private static final String WEATHER_DATA_TOPIC = "weather-data";
    private static final String TEMPERATURE_ALERTS_TOPIC = "temperature-alerts";

    private KafkaStreams streams;
    private static final ObjectMapper objectMapper = new ObjectMapper(); // Jackson ObjectMapper for parsing JSON

    public void start() {
        logger.info("Starting TemperatureComparator...");

        // Kafka stream properties
        Properties props = new Properties();
        props.put("application.id", "temperature-comparator");
        String bootstrapServers = System.getenv("KAFKA_BOOTSTRAP_SERVERS");
        if (bootstrapServers == null || bootstrapServers.isEmpty()) {
            bootstrapServers = "kafka:9092"; // Default to Docker service name if not set
        }
        props.put("bootstrap.servers", bootstrapServers);
        props.put("default.key.serde", Serdes.String().getClass());
        props.put("default.value.serde", Serdes.String().getClass());

        BigDecimal threshold = getTemperatureThreshold();

        StreamsBuilder builder = new StreamsBuilder();

        // Read streams from Kafka topics
        KStream<String, String> climateStream = builder.stream(CLIMATE_DATA_TOPIC);
        KStream<String, String> weatherStream = builder.stream(WEATHER_DATA_TOPIC);

        climateStream.peek((key, value) -> logger.info("ClimateData: key={}, value={}", key, value));
        weatherStream.peek((key, value) -> logger.info("WeatherData: key={}, value={}", key, value));

        // Join the streams by key
        logger.info("TemperatureComparator... creating joinedStream");
        logger.info("TemperatureComparator... ");

        // Join the streams by key
        KStream<String, String> joinedStream = climateStream.join(
                weatherStream,
                (climateData, weatherData) -> {
                    logger.info("TemperatureComparator... Created kstream");
                    try {
                        logger.info("TemperatureComparator... In try");
                        JsonNode climateJson = objectMapper.readTree(climateData);
                        JsonNode weatherJson = objectMapper.readTree(weatherData);

                        logger.info("TemperatureComparator... read values: " + climateData + "\n" + weatherData);
                        BigDecimal[] climateTemperatures = parseTemperatureArray(climateJson.get("daily").get("temperature_2m_mean"));
                        BigDecimal[] weatherTemperatures = parseTemperatureArray(weatherJson.get("daily").get("temperature_2m_mean"));

                        logger.info("TemperatureComparator... Comparing values");
                        for (int i = 0; i < Math.min(climateTemperatures.length, weatherTemperatures.length); i++) {
                            BigDecimal deviation = climateTemperatures[i].subtract(weatherTemperatures[i]).abs();
                            if (deviation.compareTo(threshold) > 0) {
                                logger.info("TemperatureComparator... Found a scary value: " + climateTemperatures[i] + " : " + weatherTemperatures[i]);
                                return generateAlert(climateJson, weatherJson, deviation, climateTemperatures[i], weatherTemperatures[i]);
                            }
                        }
                    } catch (Exception e) {
                        logger.error("Error processing data", e);
                    }
                    return null; // No alert
                },
                JoinWindows.of(Duration.ofMinutes(5)),
                StreamJoined.with(Serdes.String(), Serdes.String(), Serdes.String())
        );

        joinedStream
                .filter((key, value) -> value != null)
                .to(TEMPERATURE_ALERTS_TOPIC, Produced.with(Serdes.String(), Serdes.String()));

        Topology topology = builder.build();
        streams = new KafkaStreams(topology, props);
        Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
        streams.start();
    }

    public void stop() {
        if (streams != null) {
            streams.close();
        }
    }

    private BigDecimal getTemperatureThreshold() {
        String thresholdStr = System.getenv("TEMPERATURE_DEVIATION_THRESHOLD");
        BigDecimal defaultThreshold = BigDecimal.valueOf(5.0);
        if (thresholdStr != null) {
            try {
                return new BigDecimal(thresholdStr);
            } catch (NumberFormatException e) {
                logger.warn("Invalid TEMPERATURE_DEVIATION_THRESHOLD value. Using default: {}", defaultThreshold);
            }
        }
        return defaultThreshold;
    }

    private BigDecimal[] parseTemperatureArray(JsonNode jsonArray) {
        if (jsonArray == null || !jsonArray.isArray()) {
            return new BigDecimal[0];
        }
        BigDecimal[] temperatures = new BigDecimal[jsonArray.size()];
        for (int i = 0; i < jsonArray.size(); i++) {
            temperatures[i] = BigDecimal.valueOf(jsonArray.get(i).asDouble());
        }
        return temperatures;
    }

    private String generateAlert(JsonNode climateJson, JsonNode weatherJson, BigDecimal deviation, BigDecimal climateTemp, BigDecimal weatherTemp) {
        try {
            logger.info("TemperatureComparator... Generating alert JSON");
            return objectMapper.writeValueAsString(new Alert(
                    climateJson.get("latitude").decimalValue(),
                    climateJson.get("longitude").decimalValue(),
                    deviation,
                    climateTemp,
                    weatherTemp
            ));
        } catch (Exception e) {
            logger.error("Error generating alert JSON", e);
            return null;
        }
    }

    static class Alert {
        public BigDecimal latitude;
        public BigDecimal longitude;
        public BigDecimal deviation;
        public BigDecimal climateTemperature;
        public BigDecimal weatherTemperature;

        public Alert(BigDecimal latitude, BigDecimal longitude, BigDecimal deviation, BigDecimal climateTemperature, BigDecimal weatherTemperature) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.deviation = deviation;
            this.climateTemperature = climateTemperature;
            this.weatherTemperature = weatherTemperature;
        }
    }
}