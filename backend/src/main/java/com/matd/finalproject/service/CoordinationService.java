package com.matd.finalproject.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;

public class CoordinationService {

    private static final Logger logger = LoggerFactory.getLogger(CoordinationService.class);

    private final LinkedBlockingQueue<BigDecimal[]> coordinateQueue = new LinkedBlockingQueue<>();
    private final CoordinateGenerator coordinateGenerator = new CoordinateGenerator();

    private boolean climateReady = false;
    private boolean weatherReady = false;
    private int servicesRemainingForCurrentUUID = 2;

    // Track whether each producer has consumed the current UUID
    private final Map<String, Boolean> producerUUIDState = new HashMap<>();

    // Singleton instance for global access
    private static final CoordinationService instance = new CoordinationService();

    private CoordinationService() {
        // Initialize tracking for producer UUID states
        producerUUIDState.put("ClimateProducer", false);
        producerUUIDState.put("WeatherProducer", false);

        // Preload all coordinates into the queue at initialization
        preloadCoordinates();
    }

    public static CoordinationService getInstance() {
        return instance;
    }

    // Preloads all coordinates into the queue at startup
    private void preloadCoordinates() {
        try {
            logger.info("Preloading all coordinates into the queue...");
            for (BigDecimal[] coord : coordinateGenerator.getAllCoordinates()) {
                coordinateQueue.put(coord);
            }
            logger.info("All coordinates successfully preloaded into the queue.");
        } catch (InterruptedException e) {
            logger.error("Error while preloading coordinates into the queue.", e);
            Thread.currentThread().interrupt();
        }
    }

    // Mark the ClimateProducer as ready
    public synchronized void markClimateReady() {
        logger.info("CoordinationService: Climate marked as ready.");
        climateReady = true;
        notifyAll();
    }

    // Mark the WeatherProducer as ready
    public synchronized void markWeatherReady() {
        logger.info("CoordinationService: Weather marked as ready.");
        weatherReady = true;
        notifyAll();
    }

    // Wait until both ClimateProducer and WeatherProducer are ready
    public synchronized void waitForBothReady() {
        long startTime = System.currentTimeMillis();
        long timeoutMs = 60000; // 1 minute timeout

        while (!climateReady || !weatherReady) {
            try {
                long timeElapsed = System.currentTimeMillis() - startTime;
                long waitTime = Math.min(1000, timeoutMs - timeElapsed);

                if (timeElapsed >= timeoutMs) {
                    logger.warn("Timed out waiting for both producers to be ready.");
                    throw new RuntimeException("Timed out waiting for both producers to be ready.");
                }

                wait(waitTime); // Wait for up to 1 second or the remaining timeout
            } catch (InterruptedException e) {
                logger.warn("Thread interrupted while waiting for readiness.");
                Thread.currentThread().interrupt();
                return;
            }
        }

        logger.info("Both ClimateProducer and WeatherProducer are ready.");
    }

    public synchronized UUID getSharedUUID(String producerName) {
        while (Boolean.TRUE.equals(producerUUIDState.get(producerName))) {
            try {
                logger.info("{} is waiting for a new UUID since it already consumed the current one.", producerName);
                wait(); // Block until a new UUID is available
            } catch (InterruptedException e) {
                logger.warn("Thread interrupted while waiting for a new UUID.");
                Thread.currentThread().interrupt();
                throw new RuntimeException("Thread interrupted while waiting for UUID.", e);
            }
        }

        logger.info("{} retrieved the shared UUID: {}", producerName, coordinateGenerator.getOrGenerateUUID());
        return coordinateGenerator.getOrGenerateUUID();
    }

    public synchronized void notifyUUIDConsumed(String producerName) {
        producerUUIDState.put(producerName, true); // Mark that the producer has consumed the current UUID
        logger.info("{} has consumed the UUID: {}", producerName, coordinateGenerator.getOrGenerateUUID());

        servicesRemainingForCurrentUUID--;

        if (servicesRemainingForCurrentUUID <= 0) {
            logger.info("All producers have consumed the UUID. Generating a new UUID...");
            coordinateGenerator.markUUIDAsConsumed();

            // Reset for the next UUID cycle
            producerUUIDState.replaceAll((key, value) -> false); // Mark all producers as not having consumed the new UUID
            servicesRemainingForCurrentUUID = producerUUIDState.size();

            notifyAll(); // Notify all waiting threads a new UUID is now available
        } else {
            logger.info("Waiting for other producers to consume the UUID. Remaining: {}", servicesRemainingForCurrentUUID);
        }
    }

    // Synchronized coordinate access method that ensures coordination between producers
    public void coordinateAccess(Runnable action) {
        synchronized (this) {
            try {
                logger.info("Attempting to access coordinate...");
                action.run(); // Perform the supplied action (e.g., data-fetching logic in producers)
            } catch (Exception e) {
                logger.error("Error during coordinate access operation.", e);
            }
        }
    }

    // Get the next coordinate from the queue (blocks if the queue is empty)
    public BigDecimal[] getNextCoordinate() {
        try {
            return coordinateQueue.take(); // `take()` will block if the queue is empty
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Restore interrupt status
            logger.error("Thread interrupted while waiting to take a coordinate from the queue.");
            throw new RuntimeException("Could not retrieve coordinate from the queue", e);
        }
    }
}