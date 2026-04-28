package com.monitor.amazon.dto;

import com.monitor.amazon.domain.PriceCheck;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
public class PriceCheckResponse {
    private final Long id;
    private final BigDecimal price;
    private final String currency;
    private final String status;
    private final String errorMsg;
    private final LocalDateTime checkedAt;

    public PriceCheckResponse(PriceCheck check) {
        this.id = check.getId();
        this.price = check.getPrice();
        this.currency = check.getCurrency();
        this.status = check.getStatus();
        this.errorMsg = check.getErrorMsg();
        this.checkedAt = check.getCheckedAt();
    }
}
