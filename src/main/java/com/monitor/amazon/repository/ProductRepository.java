package com.monitor.amazon.repository;

import com.monitor.amazon.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findAllByActiveTrue();

    boolean existsByUrl(String url);
}
