package com.foodrescue.catalog.infrastructure.adapter.out.persistence;

import com.foodrescue.catalog.application.port.out.IPublicBatchRepository;
import com.foodrescue.catalog.domain.model.RescueBatch;
import com.foodrescue.catalog.infrastructure.adapter.out.persistence.entity.RescueBatchEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JpaPublicBatchAdapter implements IPublicBatchRepository {
    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public Page<RescueBatch> search(Collection<Long> approvedStoreIds, Collection<Long> storeNameMatches,
            Long storeId, Long categoryId, String keyword, BigDecimal minPrice, BigDecimal maxPrice,
            LocalDateTime pickupStartAfter, LocalDateTime pickupEndBefore, LocalDateTime now,
            String sort, Pageable pageable) {
        if (approvedStoreIds == null || approvedStoreIds.isEmpty()) return Page.empty(pageable);
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<RescueBatchEntity> query = builder.createQuery(RescueBatchEntity.class);
        Root<RescueBatchEntity> batch = query.from(RescueBatchEntity.class);
        query.select(batch).where(predicates(builder, query, batch, approvedStoreIds, storeNameMatches, storeId,
                categoryId, keyword, minPrice, maxPrice, pickupStartAfter, pickupEndBefore, now));
        query.orderBy(orders(builder, batch, sort));
        List<RescueBatchEntity> content = entityManager.createQuery(query)
                .setFirstResult(Math.toIntExact(pageable.getOffset()))
                .setMaxResults(pageable.getPageSize()).getResultList();

        CriteriaQuery<Long> countQuery = builder.createQuery(Long.class);
        Root<RescueBatchEntity> countBatch = countQuery.from(RescueBatchEntity.class);
        countQuery.select(builder.count(countBatch)).where(predicates(builder, countQuery, countBatch,
                approvedStoreIds, storeNameMatches, storeId, categoryId, keyword, minPrice, maxPrice,
                pickupStartAfter, pickupEndBefore, now));
        long total = entityManager.createQuery(countQuery).getSingleResult();
        return new PageImpl<>(content.stream().map(this::toDomain).toList(), pageable, total);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RescueBatch> findById(Long batchId) {
        RescueBatchEntity entity = entityManager.find(RescueBatchEntity.class, batchId);
        if (entity == null || entity.getDeletedAt() != null) return Optional.empty();
        return Optional.of(toDomain(entity));
    }

    private Predicate[] predicates(CriteriaBuilder builder, CriteriaQuery<?> query,
            Root<RescueBatchEntity> batch, Collection<Long> approvedStoreIds,
            Collection<Long> storeNameMatches, Long storeId, Long categoryId, String keyword,
            BigDecimal minPrice, BigDecimal maxPrice, LocalDateTime pickupStartAfter,
            LocalDateTime pickupEndBefore, LocalDateTime now) {
        List<Predicate> predicates = new ArrayList<>();
        predicates.add(batch.get("storeId").in(approvedStoreIds));
        predicates.add(builder.isNull(batch.get("deletedAt")));
        predicates.add(builder.greaterThan(batch.<LocalDateTime>get("pickupEnd"), now));
        Predicate available = builder.and(builder.equal(batch.get("status"), "AVAILABLE"),
                builder.greaterThan(batch.<Integer>get("remainingQuantity"), 0));
        Predicate soldOut = builder.and(builder.equal(batch.get("status"), "SOLD_OUT"),
                builder.equal(batch.<Integer>get("remainingQuantity"), 0));
        predicates.add(builder.or(available, soldOut));
        if (storeId != null) predicates.add(builder.equal(batch.get("storeId"), storeId));
        if (categoryId != null) predicates.add(builder.equal(batch.get("categoryId"), categoryId));
        if (minPrice != null) predicates.add(builder.greaterThanOrEqualTo(
                batch.<BigDecimal>get("rescuePrice"), minPrice));
        if (maxPrice != null) predicates.add(builder.lessThanOrEqualTo(
                batch.<BigDecimal>get("rescuePrice"), maxPrice));
        if (pickupStartAfter != null) predicates.add(builder.greaterThanOrEqualTo(
                batch.<LocalDateTime>get("pickupStart"), pickupStartAfter));
        if (pickupEndBefore != null) predicates.add(builder.lessThanOrEqualTo(
                batch.<LocalDateTime>get("pickupEnd"), pickupEndBefore));
        if (keyword != null && !keyword.isBlank()) {
            String escapedKeyword = keyword.toLowerCase(java.util.Locale.ROOT)
                    .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            String pattern = "%" + escapedKeyword + "%";
            Predicate textMatch = builder.or(
                    builder.like(builder.lower(batch.get("title")), pattern, '\\'),
                    builder.like(builder.lower(batch.get("description")), pattern, '\\'),
                    categoryNameMatch(builder, query, batch, pattern));
            if (storeNameMatches != null && !storeNameMatches.isEmpty()) {
                textMatch = builder.or(textMatch, batch.get("storeId").in(storeNameMatches));
            }
            predicates.add(textMatch);
        }
        return predicates.toArray(Predicate[]::new);
    }

    private Predicate categoryNameMatch(CriteriaBuilder builder, CriteriaQuery<?> query,
            Root<RescueBatchEntity> batch, String pattern) {
        Subquery<Long> category = query.subquery(Long.class);
        Root<com.foodrescue.catalog.infrastructure.adapter.out.persistence.entity.CategoryEntity> row =
                category.from(com.foodrescue.catalog.infrastructure.adapter.out.persistence.entity.CategoryEntity.class);
        category.select(row.get("id")).where(
                builder.equal(row.get("id"), batch.get("categoryId")),
                builder.equal(row.get("storeId"), batch.get("storeId")),
                builder.like(builder.lower(row.get("name")), pattern, '\\'));
        return builder.exists(category);
    }

    private List<jakarta.persistence.criteria.Order> orders(CriteriaBuilder builder,
            Root<RescueBatchEntity> batch, String sort) {
        List<jakarta.persistence.criteria.Order> orders = new ArrayList<>();
        switch (sort) {
            case "price_asc" -> orders.add(builder.asc(batch.get("rescuePrice")));
            case "price_desc" -> orders.add(builder.desc(batch.get("rescuePrice")));
            case "ending_soon" -> orders.add(builder.asc(batch.get("pickupEnd")));
            case "discount_desc" -> orders.add(builder.desc(builder.quot(
                    builder.diff(batch.<BigDecimal>get("originalPrice"), batch.<BigDecimal>get("rescuePrice")),
                    batch.<BigDecimal>get("originalPrice"))));
            default -> orders.add(builder.desc(batch.get("createdAt")));
        }
        orders.add(builder.desc(batch.get("id")));
        return orders;
    }

    private RescueBatch toDomain(RescueBatchEntity entity) {
        return new RescueBatch(entity.getId(), entity.getStoreId(), entity.getCategoryId(), entity.getTitle(),
                entity.getDescription(), entity.getImageUrl(), entity.getOriginalPrice(), entity.getRescuePrice(),
                entity.getQuantity(), entity.getRemainingQuantity(), entity.getPickupStart(), entity.getPickupEnd(),
                entity.getStatus(), entity.getCreatedAt(), entity.getAggregateVersion(),
                entity.getDeletedAt() != null);
    }
}
