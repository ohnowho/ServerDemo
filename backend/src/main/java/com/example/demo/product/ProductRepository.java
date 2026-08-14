package com.example.demo.product;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Atomic conditional stock deduction. A single SQL statement so concurrent
     * requests cannot oversell: affected rows == 1 means deducted, 0 means
     * product missing or insufficient stock.
     */
    @Modifying
    @Query("update Product p set p.stock = p.stock - :qty where p.id = :id and p.stock >= :qty")
    int deductStock(@Param("id") long id, @Param("qty") int qty);

    @Modifying
    @Query("update Product p set p.stock = p.stock + :qty where p.id = :id")
    int restoreStock(@Param("id") long id, @Param("qty") int qty);

    @Modifying
    @Query("update Product p set p.stock = p.stock + :delta where p.id = :id and p.stock + :delta >= 0")
    int adjustStock(@Param("id") long id, @Param("delta") int delta);

    List<Product> findByStatusOrderByIdAsc(ProductStatus status);
}
