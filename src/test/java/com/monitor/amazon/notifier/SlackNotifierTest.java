package com.monitor.amazon.notifier;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.monitor.amazon.config.MonitorProperties;
import com.monitor.amazon.domain.Product;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.*;

class SlackNotifierTest {

    private WireMockServer wireMock;
    private SlackNotifier notifier;

    @BeforeEach
    void setUp() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();

        wireMock.stubFor(post(urlPathEqualTo("/slack"))
                .willReturn(aResponse().withStatus(200).withBody("ok")));

        MonitorProperties props = new MonitorProperties();
        props.getSlack().setWebhookUrl("http://localhost:" + wireMock.port() + "/slack");
        notifier = new SlackNotifier(props);
    }

    @AfterEach
    void tearDown() {
        wireMock.stop();
    }

    @Test
    void sendsPostToSlackWebhook() {
        Product product = Product.builder()
                .id(1L)
                .name("Test Product")
                .url("https://www.amazon.com/dp/TEST")
                .active(true)
                .build();

        assertThatCode(() -> notifier.send(product, new BigDecimal("10.00"), new BigDecimal("8.00")))
                .doesNotThrowAnyException();

        wireMock.verify(postRequestedFor(urlPathEqualTo("/slack"))
                .withHeader("Content-Type", containing("application/json"))
                .withRequestBody(containing("Price Drop Alert")));
    }

    @Test
    void doesNotThrowWhenSlackFails() {
        wireMock.stubFor(post(urlPathEqualTo("/slack"))
                .willReturn(aResponse().withStatus(500)));

        Product product = Product.builder()
                .id(1L).name("Test").url("https://amazon.com/dp/X").active(true).build();

        assertThatCode(() -> notifier.send(product, new BigDecimal("10.00"), new BigDecimal("8.00")))
                .doesNotThrowAnyException();
    }
}
