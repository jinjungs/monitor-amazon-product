package com.monitor.amazon.scraper;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class ScrapeResult {
    private final BigDecimal price;
    private final String currency;
    private final String productName;
}
