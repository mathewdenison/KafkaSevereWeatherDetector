package com.matd.finalproject.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CoordinateGenerator {

    private static final Logger logger = LoggerFactory.getLogger(CoordinateGenerator.class);

    private static final BigDecimal DEFAULT_LAT_START = BigDecimal.valueOf(-90.0);
    private static final BigDecimal DEFAULT_LAT_END = BigDecimal.valueOf(90.0);
    private static final BigDecimal DEFAULT_LON_START = BigDecimal.valueOf(-180.0);
    private static final BigDecimal DEFAULT_LON_END = BigDecimal.valueOf(180.0);
    private static final BigDecimal DEFAULT_STEP_SIZE = BigDecimal.valueOf(1.0);

    private BigDecimal latStart = DEFAULT_LAT_START;
    private BigDecimal latEnd = DEFAULT_LAT_END;
    private BigDecimal lonStart = DEFAULT_LON_START;
    private BigDecimal lonEnd = DEFAULT_LON_END;
    private BigDecimal stepSize = DEFAULT_STEP_SIZE;

    private List<BigDecimal[]> allCoordinates = new ArrayList<>();

    private UUID currentUUID; // The current UUID that will be reused until all services retrieve it
    private boolean isUUIDConsumedByAllServices = true;

    public CoordinateGenerator() {
        // Precompute the full list of coordinates at initialization
        generateCoordinates();
    }

    // Generate the full list of coordinates based on the current boundaries and step size
    private void generateCoordinates() {
        logger.info("Generating coordinates with step size: {}", stepSize);
        allCoordinates.clear();

        for (BigDecimal lat = latStart; lat.compareTo(latEnd) <= 0; lat = lat.add(stepSize)) {
            for (BigDecimal lon = lonStart; lon.compareTo(lonEnd) <= 0; lon = lon.add(stepSize)) {
                allCoordinates.add(new BigDecimal[]{truncate(lat), truncate(lon)});
            }
        }

        logger.info("Generated {} total coordinates.", allCoordinates.size());
    }

    // Truncate BigDecimal to 3 decimal places
    private BigDecimal truncate(BigDecimal value) {
        return value.setScale(3, RoundingMode.DOWN);
    }

    // Retrieve the precomputed list of all coordinates
    public List<BigDecimal[]> getAllCoordinates() {
        return new ArrayList<>(allCoordinates); // Return a copy to preserve immutability
    }

    // Reinitialize coordinates based on new boundaries and step size
    public void reinitializeCoordinates(double newLatStart, double newLatEnd, double newLonStart, double newLonEnd, double newStepSize) {
        logger.info("Reinitializing coordinates with new boundaries and step size...");
        this.latStart = BigDecimal.valueOf(newLatStart);
        this.latEnd = BigDecimal.valueOf(newLatEnd);
        this.lonStart = BigDecimal.valueOf(newLonStart);
        this.lonEnd = BigDecimal.valueOf(newLonEnd);
        this.stepSize = BigDecimal.valueOf(newStepSize);

        // Regenerate coordinates based on the new parameters
        generateCoordinates();
        logger.info("Reinitialized coordinates with new parameters.");
    }

    public synchronized UUID getOrGenerateUUID() {
        if (isUUIDConsumedByAllServices) {
            currentUUID = UUID.randomUUID();
            isUUIDConsumedByAllServices = false; // Mark that services need to use this UUID
            logger.info("Generated a new UUID: {}", currentUUID);
        } else {
            logger.info("Returning existing UUID: {}", currentUUID);
        }
        return currentUUID;
    }

    public synchronized void markUUIDAsConsumed() {
        logger.info("Marking current UUID as consumed by all services: {}", currentUUID);
        isUUIDConsumedByAllServices = true;
    }
}