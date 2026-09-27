package com.foodrescue.common.entity;

import com.foodrescue.common.enums.PaymentTxnStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Bảng payments: 1 giao dịch VNPay. */
@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false, precision = 12, scale = 0)
    private BigDecimal amount;

    /** vnp_TxnRef */
    @Column(nullable = false, unique = true, length = 64)
    private String txnRef;

    /** vnp_TransactionNo (cần khi hoàn tiền) */
    @Column(length = 64)
    private String gatewayTxnNo;

    @Column(length = 20)
    private String bankCode;

    @Column(length = 10)
    private String responseCode;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private PaymentTxnStatus status = PaymentTxnStatus.PENDING;

    private LocalDateTime paidAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
