package com.monitor.amazon.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "monitor")
public class MonitorProperties {

    private long intervalMs = 3_600_000;
    private ThreadPool threadPool = new ThreadPool();
    private Threshold threshold = new Threshold();
    private Slack slack = new Slack();

    @Getter
    @Setter
    public static class ThreadPool {
        private int coreSize = 3;
        private int maxSize = 10;
    }

    @Getter
    @Setter
    public static class Threshold {
        private BigDecimal absolute = new BigDecimal("1.00");
        private BigDecimal percentage = new BigDecimal("2.0");
    }

    @Getter
    @Setter
    public static class Slack {
        private String webhookUrl;
    }
}
