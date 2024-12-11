package com.matd.finalproject.spark;

import org.apache.spark.sql.*;
import org.apache.spark.sql.expressions.UserDefinedAggregateFunction;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.Row;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service
public class ClimateProcessingWithSpark {

    private static final Logger logger = LoggerFactory.getLogger(ClimateProcessingWithSpark.class);

    public void processClimateDataWithSpark(SparkSession spark, String inputFilePath, String kafkaBootstrapServers, String kafkaTopic) {
        // Load climate data file
        Dataset<Row> climateData = spark.read().json(inputFilePath);

        logger.info("ClimateProcessingWithSpark: Loaded climate data schema:");
        climateData.printSchema();

        logger.info("ClimateProcessingWithSpark: Raw climate data:");
        climateData.show(5);


        // Extract relevant fields and transformations
        Dataset<Row> climateDaily = climateData.select(
                climateData.col("latitude"),
                climateData.col("longitude"),
                climateData.col("generationtime_ms"),
                climateData.col("utc_offset_seconds"),
                climateData.col("timezone"),
                climateData.col("timezone_abbreviation"),
                climateData.col("elevation"),
                climateData.col("daily_units"),
                climateData.col("daily.time").alias("daily_time"),
                climateData.col("daily.temperature_2m_mean").alias("daily_temperature_2m_mean")
        );

        logger.info("ClimateProcessingWithSpark: Extracted and transformed daily data:");
        climateDaily.show(5);

        // Reassemble the full structure as required
        Dataset<Row> processedClimateData = climateDaily.withColumn(
                "daily", functions.struct(
                        climateDaily.col("daily_time").alias("time"),
                        climateDaily.col("daily_temperature_2m_mean").alias("temperature_2m_mean")
                )
        ).drop("daily_time", "daily_temperature_2m_mean");

        // Serialize the entire dataset into JSON for Kafka
        Dataset<Row> kafkaData = processedClimateData.select(
                functions.to_json(functions.struct("*")).alias("value") // Convert entire structure to JSON
        );

        logger.info("ClimateProcessingWithSpark: Preview of data to be written to Kafka:");
        kafkaData.show(false);

        // Publish the results to Kafka (key/value structure)
        try {
            kafkaData
                    .write()
                    .format("kafka")
                    .option("kafka.bootstrap.servers", kafkaBootstrapServers)
                    .option("topic", kafkaTopic)
                    .save();
            logger.info("Data successfully written to Kafka topic: " + kafkaTopic);
        } catch (Exception e) {
            logger.error("Error while writing data to Kafka:", e);
        }
    }

    public void run(String inputFilePath, String kafkaBootstrapServers, String kafkaTopic) {
        // Validate input arguments

        logger.info("ClimateProcessingWithSpark: Before if");
        // Retrieve Kafka bootstrap servers from environment variables (default to localhost:9092 if not set)
        if (kafkaBootstrapServers == null || kafkaBootstrapServers.isEmpty()) {
            logger.info("ClimateProcessingWithSpark: In if");
            kafkaBootstrapServers = "localhost:9092";
        }

        logger.info("ClimateProcessingWithSpark: bootstrap servers: " + kafkaBootstrapServers);
        // Initialize Spark
        SparkSession spark = null;

        try {
            spark = SparkSession.builder()
                    .appName("Climate Processing With Spark")
                    .master("local[*]")
                    .config("spark.ui.showConsoleProgress", "true")
                    .config("spark.eventLog.enabled", "true")
                    .config("spark.eventLog.dir", "/app")
                    .config("spark.sql.streaming.stateStore.formatTracking.enable", "true")
                    .config("spark.executor.logs.rolling.enable", "true")
                    .getOrCreate();

            logger.info("Successfully created SparkSession.");

        } catch (Exception e) {
            logger.error("Error occurred while creating SparkSession: ", e);
            throw e;
        }

        logger.info("ClimateProcessingWithSpark: built a spark session");
        processClimateDataWithSpark(spark, inputFilePath, kafkaBootstrapServers, kafkaTopic);

        logger.info("ClimateProcessingWithSpark: processed climate data with spark");
        spark.stop();
    }
}