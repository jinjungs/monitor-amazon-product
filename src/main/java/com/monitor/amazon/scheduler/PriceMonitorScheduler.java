package com.monitor.amazon.scheduler;

import com.monitor.amazon.domain.Product;
import com.monitor.amazon.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PriceMonitorScheduler {

    private final ProductRepository productRepository;
    private final PriceMonitorService priceMonitorService;

    @Scheduled(fixedRateString = "${monitor.interval-ms}")
    public void runPriceChecks() {
        List<Product> products = productRepository.findAllByActiveTrue();
        log.info("Scheduled price check started productCount={}", products.size());
        products.forEach(priceMonitorService::checkProduct);
    }
}
