package com.monitor.amazon.checker;

import com.monitor.amazon.config.MonitorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Component
@RequiredArgsConstructor
public class PriceChecker {

    private final MonitorProperties properties;

    public boolean isSignificantDrop(BigDecimal oldPrice, BigDecimal newPrice) {
        if (oldPrice == null || newPrice == null) {
            return false;
        }
        if (newPrice.compareTo(oldPrice) >= 0) {
            return false;
        }

        BigDecimal drop = oldPrice.subtract(newPrice);
        boolean absoluteDrop = drop.compareTo(properties.getThreshold().getAbsolute()) >= 0;

        BigDecimal dropPercent = drop
                .divide(oldPrice, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        boolean percentageDrop = dropPercent.compareTo(properties.getThreshold().getPercentage()) >= 0;

        log.debug("Price check: old={} new={} drop={} dropPercent={} absoluteDrop={} percentageDrop={}",
                oldPrice, newPrice, drop, dropPercent, absoluteDrop, percentageDrop);

        return absoluteDrop || percentageDrop;
    }
}
