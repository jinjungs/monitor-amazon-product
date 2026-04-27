package com.monitor.amazon.scheduler;

import com.monitor.amazon.checker.PriceChecker;
import com.monitor.amazon.domain.PriceCheck;
import com.monitor.amazon.domain.Product;
import com.monitor.amazon.exception.CaptchaException;
import com.monitor.amazon.exception.PriceParseException;
import com.monitor.amazon.notifier.Notifier;
import com.monitor.amazon.repository.PriceCheckRepository;
import com.monitor.amazon.scraper.AmazonScraper;
import com.monitor.amazon.scraper.ScrapeResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PriceMonitorService {

    private final AmazonScraper scraper;
    private final PriceCheckRepository priceCheckRepository;
    private final PriceChecker priceChecker;
    private final Notifier notifier;

    @Async("priceCheckExecutor")
    @Transactional
    public void checkProduct(Product product) {
        log.info("Starting price check productId={} url={}", product.getId(), product.getUrl());
        long start = System.currentTimeMillis();

        Optional<PriceCheck> lastCheck = priceCheckRepository.findLastSuccessfulByProductId(product.getId());
        BigDecimal lastPrice = lastCheck.map(PriceCheck::getPrice).orElse(null);

        try {
            ScrapeResult result = scraper.scrape(product.getUrl());

            if (product.getName() == null || product.getName().isBlank()) {
                product.setName(result.getProductName());
            }

            priceCheckRepository.save(PriceCheck.builder()
                    .product(product)
                    .price(result.getPrice())
                    .currency(result.getCurrency())
                    .status("ok")
                    .build());

            log.info("Price check ok productId={} price={} durationMs={}",
                    product.getId(), result.getPrice(), System.currentTimeMillis() - start);

            if (lastPrice != null && priceChecker.isSignificantDrop(lastPrice, result.getPrice())) {
                notifier.send(product, lastPrice, result.getPrice());
            }

        } catch (CaptchaException e) {
            saveFailedCheck(product, "unavailable", e.getMessage());
            log.warn("CAPTCHA detected productId={} url={}", product.getId(), product.getUrl());
        } catch (PriceParseException e) {
            saveFailedCheck(product, "unavailable", e.getMessage());
            log.warn("Price element not found productId={} url={}", product.getId(), product.getUrl());
        } catch (Exception e) {
            saveFailedCheck(product, "error", e.getMessage());
            log.error("Price check failed productId={} error={} durationMs={}",
                    product.getId(), e.getMessage(), System.currentTimeMillis() - start);
        }
    }

    private void saveFailedCheck(Product product, String status, String errorMsg) {
        priceCheckRepository.save(PriceCheck.builder()
                .product(product)
                .status(status)
                .errorMsg(errorMsg)
                .build());
    }
}
