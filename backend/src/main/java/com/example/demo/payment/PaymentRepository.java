package com.example.demo.payment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<PaymentRecord, Long> {

    Optional<PaymentRecord> findByPaymentNo(String paymentNo);

    List<PaymentRecord> findByOrderNoOrderByIdAsc(String orderNo);

    /** Latest payment (non-refund) attempt for an order. */
    @Query("select p from PaymentRecord p where p.orderNo = :orderNo and p.type = com.example.demo.payment.PaymentType.PAYMENT order by p.id desc")
    List<PaymentRecord> findPaymentsByOrderNo(@Param("orderNo") String orderNo);

    /** CREATED -> SUCCESS, only once. Records the channel-side trade number and paid time. */
    @Modifying(clearAutomatically = true)
    @Query("update PaymentRecord p set p.status = :to, p.channelTradeNo = :tradeNo, p.paidAt = :paidAt " +
           "where p.paymentNo = :paymentNo and p.status = :from")
    int markPaidIfCreated(@Param("paymentNo") String paymentNo, @Param("from") PaymentStatus from,
                          @Param("to") PaymentStatus to, @Param("tradeNo") String tradeNo,
                          @Param("paidAt") Instant paidAt);

    /** CREATED -> FAILED (e.g. channel reports the trade as closed). */
    @Modifying(clearAutomatically = true)
    @Query("update PaymentRecord p set p.status = :to where p.paymentNo = :paymentNo and p.status = :from")
    int closeIfCreated(@Param("paymentNo") String paymentNo, @Param("from") PaymentStatus from,
                       @Param("to") PaymentStatus to);

    @Modifying(clearAutomatically = true)
    @Query("update PaymentRecord p set p.payload = :payload where p.paymentNo = :paymentNo")
    int updatePayload(@Param("paymentNo") String paymentNo, @Param("payload") String payload);
}
