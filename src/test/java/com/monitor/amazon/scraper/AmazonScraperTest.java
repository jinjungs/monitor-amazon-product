package com.monitor.amazon.scraper;

import com.monitor.amazon.exception.PriceParseException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

class AmazonScraperTest {

    private AmazonScraper scraper;
    private Document fixtureDoc;

    @BeforeEach
    void setUp() throws Exception {
        scraper = new AmazonScraper();
        try (InputStream is = getClass().getResourceAsStream("/fixtures/amazon-product.html")) {
            String html = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            fixtureDoc = Jsoup.parse(html);
        }
    }

    @Test
    void parsesProductNameCorrectly() {
        ScrapeResult result = scraper.parseDocument(fixtureDoc, "http://test.com");
        assertThat(result.getProductName()).isEqualTo("Test Toothbrush 10 Pack");
    }

    @Test
    void parsesPriceCorrectly() {
        ScrapeResult result = scraper.parseDocument(fixtureDoc, "http://test.com");
        assertThat(result.getPrice()).isEqualByComparingTo(new BigDecimal("5.19"));
    }

    @Test
    void throwsPriceParseExceptionWhenNoPriceElement() {
        Document emptyDoc = Jsoup.parse("<html><body><span id='productTitle'>Test</span></body></html>");
        assertThatThrownBy(() -> scraper.parseDocument(emptyDoc, "http://test.com"))
                .isInstanceOf(PriceParseException.class);
    }
}
