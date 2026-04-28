package com.monitor.amazon.service;

import com.monitor.amazon.domain.PriceCheck;
import com.monitor.amazon.domain.Product;
import com.monitor.amazon.dto.PriceCheckResponse;
import com.monitor.amazon.dto.ProductRequest;
import com.monitor.amazon.dto.ProductResponse;
import com.monitor.amazon.repository.PriceCheckRepository;
import com.monitor.amazon.repository.ProductRepository;
import com.monitor.amazon.scheduler.PriceMonitorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final PriceCheckRepository priceCheckRepository;
    private final PriceMonitorService priceMonitorService;

    public List<ProductResponse> getAllProducts() {
        return productRepository.findAll().stream()
                .map(p -> {
                    var lastOk = priceCheckRepository.findLastSuccessfulByProductId(p.getId());
                    var lastAny = priceCheckRepository.findLastByProductId(p.getId());
                    return new ProductResponse(
                            p,
                            lastOk.map(PriceCheck::getPrice).orElse(null),
                            lastAny.map(PriceCheck::getStatus).orElse(null)
                    );
                })
                .toList();
    }

    @Transactional
    public ProductResponse addProduct(ProductRequest request) {
        Product product = Product.builder()
                .url(request.getUrl())
                .name(request.getName())
                .active(true)
                .build();
        productRepository.save(product);

        // Trigger an immediate price check after registration.
        // Runs async — product is saved regardless of scraping outcome.
        priceMonitorService.checkProduct(product);

        return new ProductResponse(product, null, null);
    }

    @Transactional
    public void deleteProduct(Long id) {
        priceCheckRepository.deleteAllByProductId(id);
        productRepository.deleteById(id);
    }

    @Transactional
    public ProductResponse toggleActive(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));
        product.setActive(!product.isActive());
        return new ProductResponse(product, null, null);
    }

    @Transactional
    public ProductResponse updateProduct(Long id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));
        product.setName(request.getName());
        product.setUrl(request.getUrl());
        return new ProductResponse(product, null, null);
    }

    public List<PriceCheckResponse> getHistory(Long productId) {
        return priceCheckRepository.findByProductIdOrderByCheckedAtDesc(productId)
                .stream()
                .map(PriceCheckResponse::new)
                .toList();
    }
}
