package com.monitor.amazon.scraper;

import com.monitor.amazon.exception.CaptchaException;
import com.monitor.amazon.exception.PriceParseException;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

import org.jsoup.HttpStatusException;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;

@Slf4j
@Component
public class AmazonScraper {

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    @Retryable(
            retryFor = {SocketTimeoutException.class, IOException.class},
            noRetryFor = {PriceParseException.class, CaptchaException.class, HttpStatusException.class},
            maxAttempts = 2,
            backoff = @Backoff(delay = 5000)
    )
    public ScrapeResult scrape(String url) throws IOException {
        log.debug("Scraping URL: {}", url);

        Document doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .header("Accept-Language", "en-US,en;q=0.9")
                .timeout(10_000)
                .get();

        if (isCaptchaPage(doc)) {
            throw new CaptchaException(url);
        }

        String productName = extractProductName(doc);
        BigDecimal price = extractPrice(doc, url);

        log.info("Scraped product={} price={} url={}", productName, price, url);
        return ScrapeResult.builder()
                .price(price)
                .currency("USD")
                .productName(productName)
                .build();
    }

    @Recover
    public ScrapeResult recover(Exception e, String url) {
        log.error("Scraping failed after retries url={} error={}", url, e.getMessage());
        throw new RuntimeException("Scraping failed for: " + url, e);
    }

    private boolean isCaptchaPage(Document doc) {
        return doc.title().toLowerCase().contains("robot check")
                || doc.selectFirst("form[action*='validateCaptcha']") != null;
    }

    private String extractProductName(Document doc) {
        Element title = doc.selectFirst("#productTitle");
        return title != null ? title.text().trim() : "Unknown Product";
    }

    private BigDecimal extractPrice(Document doc, String url) {
        // Primary: apex price (most product types)
        Element wholeEl = doc.selectFirst("span.a-price-whole");
        Element fractionEl = doc.selectFirst("span.a-price-fraction");

        if (wholeEl != null && fractionEl != null) {
            String whole = wholeEl.text().replace(".", "").replace(",", "").trim();
            String fraction = fractionEl.text().trim();
            return new BigDecimal(whole + "." + fraction);
        }

        // Fallback: offscreen price label (e.g. "$5.19")
        Element offscreen = doc.selectFirst("span.a-offscreen");
        if (offscreen != null) {
            String raw = offscreen.text().replaceAll("[^0-9.]", "");
            if (!raw.isEmpty()) {
                return new BigDecimal(raw);
            }
        }

        throw new PriceParseException("Price element not found for URL: " + url);
    }
}
