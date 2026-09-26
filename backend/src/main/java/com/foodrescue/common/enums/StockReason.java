package com.foodrescue.common.enums;

/** stock_movements.reason. Dấu của change_qty phải khớp: LISTED/ADDED/RETURNED dương, REMOVED/ORDERED âm. */
public enum StockReason {
    LISTED(true), ADDED(true), REMOVED(false), ORDERED(false), RETURNED(true);

    private final boolean increase;

    StockReason(boolean increase) {
        this.increase = increase;
    }

    public boolean isIncrease() {
        return increase;
    }

    /** Có làm thay đổi tổng số túi đã đưa lên app (rescue_batches.quantity) hay không. */
    public boolean changesListedQuantity() {
        return this == LISTED || this == ADDED || this == REMOVED;
    }
}
