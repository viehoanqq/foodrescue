package com.foodrescue.common.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/** Bảng cart_items: giỏ hàng. Khoá chính = (khách, túi). Giỏ không giữ hàng. */
@Entity
@Table(name = "cart_items")
@IdClass(CartItemId.class)
@Getter
@Setter
@NoArgsConstructor
public class CartItem {

    @Id
    @Column(name = "customer_id")
    private Long customerId;

    @Id
    @Column(name = "batch_id")
    private Long batchId;

    /** Chỉ để đọc thông tin túi; muốn đổi túi thì đổi batchId. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id", insertable = false, updatable = false)
    private RescueBatch batch;

    @Column(nullable = false)
    private int quantity;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime addedAt;
}
