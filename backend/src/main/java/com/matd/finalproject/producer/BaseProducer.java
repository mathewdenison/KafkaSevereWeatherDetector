package com.matd.finalproject.producer;

import com.matd.finalproject.service.CoordinationService;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

public abstract class BaseProducer {

    protected List<BigDecimal[]> obtainCoordinates() {
        return Collections.singletonList(CoordinationService.getInstance().getNextCoordinate());
    }
}
