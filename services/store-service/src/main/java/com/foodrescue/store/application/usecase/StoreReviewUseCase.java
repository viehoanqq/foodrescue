package com.foodrescue.store.application.usecase;

import com.foodrescue.store.application.port.out.IOrderEligibilityPort;
import com.foodrescue.store.application.port.out.IReviewRepository;
import com.foodrescue.store.application.port.out.IStoreRepository;
import com.foodrescue.store.domain.exception.StoreException;
import com.foodrescue.store.domain.model.ReviewView;

public class StoreReviewUseCase {
    private final IStoreRepository stores;
    private final IReviewRepository reviews;
    private final IOrderEligibilityPort orders;

    public StoreReviewUseCase(IStoreRepository stores, IReviewRepository reviews, IOrderEligibilityPort orders) {
        this.stores = stores;
        this.reviews = reviews;
        this.orders = orders;
    }

    public ReviewView.Item create(Long storeId, Long customerId, ReviewView.Create request) {
        if (request == null || request.orderId() == null || request.orderId() <= 0
                || request.rating() == null || request.rating() < 1 || request.rating() > 5)
            throw new StoreException(400, "INVALID_REVIEW", "Mã đơn hoặc số sao không hợp lệ");
        if (request.comment() != null && request.comment().length() > 2000)
            throw new StoreException(400, "INVALID_REVIEW", "Nhận xét không được quá 2000 ký tự");
        requireApproved(storeId);
        IOrderEligibilityPort.Eligibility eligibility = orders.check(request.orderId(), customerId);
        if (eligibility == null || !eligibility.eligible() || !storeId.equals(eligibility.storeId())
                || !customerId.equals(eligibility.customerId()))
            throw new StoreException(403, "REVIEW_NOT_ALLOWED", "Chỉ được đánh giá đơn đã hoàn thành của chính bạn tại cửa hàng này");
        String comment = request.comment() == null ? null : request.comment().trim();
        return reviews.save(storeId, customerId,
                new ReviewView.Create(request.orderId(), request.rating(), comment == null || comment.isEmpty() ? null : comment));
    }

    public ReviewView.Page list(Long storeId, int page, int size) {
        if (page < 0 || size < 1 || size > 100)
            throw new StoreException(400, "INVALID_PAGINATION", "Trang hoặc kích thước trang không hợp lệ");
        requireApproved(storeId);
        return reviews.findByStoreId(storeId, page, size);
    }

    private void requireApproved(Long storeId) {
        if (storeId == null || storeId <= 0 || stores.findApprovedById(storeId).isEmpty())
            throw new StoreException(404, "STORE_NOT_FOUND", "Không tìm thấy cửa hàng");
    }
}
