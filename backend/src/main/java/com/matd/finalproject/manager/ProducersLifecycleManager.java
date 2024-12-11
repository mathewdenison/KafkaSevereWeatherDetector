package com.matd.finalproject.manager;

import com.matd.finalproject.producer.ClimateProducer;
import com.matd.finalproject.producer.WeatherProducer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Component
public class ProducersLifecycleManager {

    @Autowired
    ClimateProducer climateProducer;
    @Autowired
    WeatherProducer weatherProducer;

    @PostConstruct
    public void startProducers() {
        climateProducer.start();
        weatherProducer.start();
    }

    @PreDestroy
    public void stopProducers() {
        climateProducer.stop();
        weatherProducer.stop();
    }
}