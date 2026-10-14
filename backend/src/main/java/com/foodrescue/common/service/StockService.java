package com.foodrescue.common.service;

import com.foodrescue.common.entity.Order;
import com.foodrescue.common.entity.RescueBatch;
import com.foodrescue.common.entity.StockMovement;
import com.foodrescue.common.entity.User;
import com.foodrescue.common.enums.StockReason;
import com.foodrescue.common.repository.StockMovementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ghi sổ kho stock_movements (chỉ thêm, CSDL có trigger chặn sửa / xoá).
 */
@Service
public class StockService {

    private final StockMovementRepository stockMovementRepository;

    public StockService(StockMovementRepository stockMovementRepository) {
        this.stockMovementRepository = stockMovementRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public StockMovement recordMovement(
            RescueBatch batch,
            int changeQty,
            StockReason reason,
            Order order,
            User actor,
            String note) {
        StockMovement movement = new StockMovement();
        movement.setBatch(batch);
        movement.setChangeQty(changeQty);
        movement.setReason(reason);
        movement.setOrder(order);
        movement.setActor(actor);
        movement.setRemainingAfter(batch.getRemainingQuantity());
        movement.setNote(note);
        return stockMovementRepository.save(movement);
    }
}
