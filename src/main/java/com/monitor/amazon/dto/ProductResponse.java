package com.monitor.amazon.dto;

import com.monitor.amazon.domain.Product;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
public class ProductResponse {
    private final Long id;
    private final String url;
    private final String name;
    private final boolean active;
    private final LocalDateTime createdAt;
    private final BigDecimal latestPrice;

    public ProductResponse(Product product, BigDecimal latestPrice) {
        this.id = product.getId();
        this.url = product.getUrl();
        this.name = product.getName();
        this.active = product.isActive();
        this.createdAt = product.getCreatedAt();
        this.latestPrice = latestPrice;
    }
}
