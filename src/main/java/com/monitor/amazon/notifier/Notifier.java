package com.monitor.amazon.notifier;

import com.monitor.amazon.domain.Product;

import java.math.BigDecimal;

public interface Notifier {
    void send(Product product, BigDecimal oldPrice, BigDecimal newPrice);
}
