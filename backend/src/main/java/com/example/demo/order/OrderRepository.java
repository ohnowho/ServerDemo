package com.example.demo.order;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNo(String orderNo);

    List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);

    /**
     * Compare-and-set transitions. Affected rows == 1 means this caller won the
     * transition and owns the side effects (stock restore etc.); 0 means the
     * order was already moved to another state and we must not touch it again.
     */
    @Modifying(clearAutomatically = true)
    @Query("update Order o set o.status = :to, o.closedAt = :closedAt where o.orderNo = :orderNo and o.status = :from")
    int closeIfPending(@Param("orderNo") String orderNo, @Param("from") OrderStatus from,
                       @Param("to") OrderStatus to, @Param("closedAt") Instant closedAt);

    @Modifying(clearAutomatically = true)
    @Query("update Order o set o.status = :to, o.paidAt = :paidAt where o.orderNo = :orderNo and o.status = :from")
    int markPaidIfPending(@Param("orderNo") String orderNo, @Param("from") OrderStatus from,
                          @Param("to") OrderStatus to, @Param("paidAt") Instant paidAt);

    @Modifying(clearAutomatically = true)
    @Query("update Order o set o.status = :to where o.orderNo = :orderNo and o.status = :from")
    int refundIfPaid(@Param("orderNo") String orderNo, @Param("from") OrderStatus from,
                     @Param("to") OrderStatus to);

    /** Expired pending orders, row-locked so a racing notify serializes with the timeout job. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.status = :status and o.expiresAt < :now")
    List<Order> findExpiredPending(@Param("status") OrderStatus status, @Param("now") Instant now);
}
