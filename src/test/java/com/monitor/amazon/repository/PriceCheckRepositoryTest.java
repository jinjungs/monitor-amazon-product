package com.monitor.amazon.repository;

import com.monitor.amazon.config.JpaConfig;
import com.monitor.amazon.domain.PriceCheck;
import com.monitor.amazon.domain.Product;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Import(JpaConfig.class)
class PriceCheckRepositoryTest {

    @Autowired private ProductRepository productRepository;
    @Autowired private PriceCheckRepository priceCheckRepository;

    private Product product;

    @BeforeEach
    void setUp() {
        product = productRepository.save(Product.builder()
                .url("https://www.amazon.com/dp/TEST123")
                .name("Test Product")
                .active(true)
                .build());
    }

    @Test
    void savesAndRetrievesPriceCheck() {
        PriceCheck saved = priceCheckRepository.save(PriceCheck.builder()
                .product(product)
                .price(new BigDecimal("9.99"))
                .status("ok")
                .build());

        assertThat(priceCheckRepository.findById(saved.getId())).isPresent();
    }

    @Test
    void returnsLastSuccessfulPrice() {
        priceCheckRepository.save(PriceCheck.builder().product(product).price(new BigDecimal("10.00")).status("ok").build());
        priceCheckRepository.save(PriceCheck.builder().product(product).status("error").errorMsg("timeout").build());
        priceCheckRepository.save(PriceCheck.builder().product(product).price(new BigDecimal("8.00")).status("ok").build());

        Optional<PriceCheck> last = priceCheckRepository.findLastSuccessfulByProductId(product.getId());

        assertThat(last).isPresent();
        assertThat(last.get().getPrice()).isEqualByComparingTo(new BigDecimal("8.00"));
    }

    @Test
    void returnsEmptyWhenNoSuccessfulCheck() {
        priceCheckRepository.save(PriceCheck.builder().product(product).status("error").build());

        Optional<PriceCheck> last = priceCheckRepository.findLastSuccessfulByProductId(product.getId());

        assertThat(last).isEmpty();
    }
}
