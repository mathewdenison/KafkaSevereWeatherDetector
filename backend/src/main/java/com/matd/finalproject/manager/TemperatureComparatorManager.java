package com.matd.finalproject.manager;

import com.matd.finalproject.kafka.TemperatureComparator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Component
public class TemperatureComparatorManager {

    @Autowired
    TemperatureComparator temperatureComparator;

    @PostConstruct
    public void startTemperatureComparator() {
        temperatureComparator.start();
    }

    @PreDestroy
    public void stopTemperatureComparator() {
        temperatureComparator.stop();
    }
}