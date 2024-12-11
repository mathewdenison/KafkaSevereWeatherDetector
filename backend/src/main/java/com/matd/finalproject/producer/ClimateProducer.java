package com.matd.finalproject.producer;

import com.matd.finalproject.service.CoordinationService;
import com.matd.finalproject.spark.ClimateProcessingWithSpark;
import org.apache.hadoop.shaded.org.eclipse.jetty.util.ajax.JSON;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.*;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONObject;

@Component
public class ClimateProducer extends BaseProducer {


    private ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final Logger logger = LoggerFactory.getLogger(ClimateProducer.class);
    private LocalDate weekStartDate;
    private LocalDate weekEndDate;
    private CoordinationService service = CoordinationService.getInstance();


    public void start() {
        logger.info("Starting Climate Producer...");
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
            bootstrapServers = "kafka:9092"; // Default
        }
        props.put("fetch.max.bytes", "2097152");
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        logger.info("Kafka Climate producer configured with servers: {}", bootstrapServers);

        KafkaProducer<String, String> producer = new KafkaProducer<>(props);
        String topic = "climate-data";

        service.markClimateReady();

        String finalBootstrapServers = bootstrapServers;
        scheduler.scheduleAtFixedRate(() -> {
            logger.info("Starting new climate data request cycle.");

            service.coordinateAccess(() -> {
                logger.info("ClimateProducer: About to check if both are ready...");
                service.waitForBothReady();

                try {
                    logger.info("ClimateProducer: In try");
                    // Get the next coordinates
                    BigDecimal[] coordinate = service.getNextCoordinate();
                    BigDecimal lat = coordinate[0];
                    BigDecimal lon = coordinate[1];

                    weekStartDate = LocalDate.now();
                    weekEndDate = weekStartDate.plusDays(6);

                    logger.info("ClimateProducer: Fetching climate data for week: {} to {}", weekStartDate, weekEndDate);

                    // Download climate data into a JSON file
                    String urlString = String.format(
                            "https://climate-api.open-meteo.com/v1/climate?latitude=%.2f&longitude=%.2f&models=CMCC_CM2_VHR4&daily=temperature_2m_mean&start_date=1950-01-01&end_date=%s",
                            lat, lon, LocalDate.now().toString()
                    );

                    HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
                    conn.setRequestMethod("GET");

                    BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder content = new StringBuilder();
                    String line;
                    while ((line = in.readLine()) != null) {
                        content.append(line);
                    }
                    in.close();
                    conn.disconnect();

                    String response = content.toString();

                    UUID uuid = service.getSharedUUID("ClimateProducer");

                    // Calculate weekly averages for the historical data
                    String processedData = processClimateData(response, weekStartDate, weekEndDate);

                    // Publish processed data to Kafka
                    producer.send(
                            new ProducerRecord<>(topic, uuid.toString(), processedData), // Fix key format
                            (RecordMetadata metadata, Exception exception) -> {
                                if (exception != null) {
                                    logger.error("Failed to send climate data to Kafka for lat: {}, lon: {}", lat, lon, exception);
                                } else {
                                    logger.info("Climate data successfully sent to Kafka topic {} at offset {}", metadata.topic(), metadata.offset());
                                }
                            }
                    );

                } catch (Exception e) {
                    logger.error("Error during climate data processing.", e);
                } finally {
                    service.markClimateReady();
                    service.notifyUUIDConsumed("ClimateProducer");
                }
            });

        }, 0, 1, TimeUnit.MINUTES); // Schedule every minute (adjust as needed)
    }


    private String processClimateData(String response, LocalDate weekStartDate, LocalDate weekEndDate) throws Exception {
        // Parse JSON response
        JSONObject jsonResponse = new JSONObject(response);

        // Extract the "daily" field containing temperature data
        JSONObject dailyData = jsonResponse.getJSONObject("daily");
        JSONArray dailyDates = dailyData.getJSONArray("time");
        JSONArray dailyTemperatures = dailyData.getJSONArray("temperature_2m_mean");

        // Map to group temperatures for each given date across years
        Map<LocalDate, List<BigDecimal>> temperatureByDate = new HashMap<>();

        // Iterate through the response and group temperatures by date
        for (int i = 0; i < dailyDates.length(); i++) {
            LocalDate currentDate = LocalDate.parse(dailyDates.getString(i));
            BigDecimal currentTemp = dailyTemperatures.isNull(i) ?
                    null :
                    new BigDecimal(dailyTemperatures.getDouble(i));

            if (currentTemp != null) {
                temperatureByDate.computeIfAbsent(currentDate, k -> new ArrayList<>()).add(currentTemp);
            }
        }

        // Calculate averages for each day in the requested week
        JSONArray averagesArray = new JSONArray();
        for (LocalDate currentDate = weekStartDate;
             !currentDate.isAfter(weekEndDate); currentDate = currentDate.plusDays(1)) {
            List<BigDecimal> tempsForDay = new ArrayList<>();

            // Collect temperatures for the current day across all years
            for (int year = 1950; year <= LocalDate.now().getYear() - 1; year++) {
                LocalDate historicalDate = currentDate.withYear(year);
                if (temperatureByDate.containsKey(historicalDate)) {
                    tempsForDay.addAll(temperatureByDate.get(historicalDate));
                }
            }

            // Calculate the average temperature for this day
            BigDecimal total = tempsForDay.stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal averageTemp = tempsForDay.isEmpty() ?
                    null :
                    total.divide(BigDecimal.valueOf(tempsForDay.size()), 2, BigDecimal.ROUND_HALF_UP);

            logger.info("Date: {}, Average Temp: {}", currentDate, averageTemp);

            // Add the results to the output
            if (averageTemp != null) {
                averagesArray.put(averageTemp);
            }
            else {
                averagesArray.put(JSONObject.NULL);
            }
        }

        // Create output JSON
        JSONObject result = new JSONObject();
        result.put("latitude", BigDecimal.valueOf(jsonResponse.getDouble("latitude")));
        result.put("longitude", BigDecimal.valueOf(jsonResponse.getDouble("longitude")));
        JSONObject temp = new JSONObject();
        temp.put("temperature_2m_mean", averagesArray);
        result.put("daily", temp);

        logger.info("Processed Climate Data: {}", result.toString(2)); // Pretty print
        return result.toString();
    }
}