package com.foodrescue.entity;

import java.io.Serializable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Khoá chính ghép của cart_items: (customer_id, batch_id). */
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class CartItemId implements Serializable {

    private Long customerId;
    private Long batchId;

    public CartItemId(Long customerId, Long batchId) {
        this.customerId = customerId;
        this.batchId = batchId;
    }
}
