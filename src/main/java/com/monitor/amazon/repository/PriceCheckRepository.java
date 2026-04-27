package com.monitor.amazon.repository;

import com.monitor.amazon.domain.PriceCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PriceCheckRepository extends JpaRepository<PriceCheck, Long> {

    @Query("""
        SELECT pc FROM PriceCheck pc
        WHERE pc.product.id = :productId AND pc.status = 'ok'
        ORDER BY pc.checkedAt DESC
        LIMIT 1
        """)
    Optional<PriceCheck> findLastSuccessfulByProductId(@Param("productId") Long productId);

    List<PriceCheck> findByProductIdOrderByCheckedAtDesc(Long productId);
}
