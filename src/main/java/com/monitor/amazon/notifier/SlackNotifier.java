package com.monitor.amazon.notifier;

import com.monitor.amazon.config.MonitorProperties;
import com.monitor.amazon.domain.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@Component
@RequiredArgsConstructor
public class SlackNotifier implements Notifier {

    private final MonitorProperties properties;
    private final RestClient restClient = RestClient.create();

    @Override
    public void send(Product product, BigDecimal oldPrice, BigDecimal newPrice) {
        BigDecimal drop = oldPrice.subtract(newPrice);
        BigDecimal dropPercent = drop
                .divide(oldPrice, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);

        String message = String.format(
                "*Price Drop Alert!* :tada:\n*%s*\n$%.2f → $%.2f (-%s%% / -$%.2f)\n<%s|View on Amazon>",
                product.getName(), oldPrice, newPrice, dropPercent, drop, product.getUrl()
        );

        String payload = "{\"text\": " + jsonEscape(message) + "}";

        try {
            restClient.post()
                    .uri(properties.getSlack().getWebhookUrl())
                    .header("Content-Type", "application/json")
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            log.info("Slack notification sent productId={} oldPrice={} newPrice={}",
                    product.getId(), oldPrice, newPrice);
        } catch (Exception e) {
            log.error("Slack notification failed productId={} error={}", product.getId(), e.getMessage());
        }
    }

    private String jsonEscape(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
