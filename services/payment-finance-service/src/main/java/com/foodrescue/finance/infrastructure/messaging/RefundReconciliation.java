package com.foodrescue.finance.infrastructure.messaging;

import com.foodrescue.finance.infrastructure.adapter.out.persistence.JpaFinanceLedger;
import com.foodrescue.finance.infrastructure.adapter.out.persistence.JpaFinanceQueries;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

@Component
public class RefundReconciliation {
    private final JpaFinanceQueries db;
    private final JpaFinanceLedger ledger;
    private final TransactionTemplate tx;
    private static final org.slf4j.Logger LOG =
            org.slf4j.LoggerFactory.getLogger(RefundReconciliation.class);

    public RefundReconciliation(
            JpaFinanceQueries db,
            JpaFinanceLedger ledger,
            org.springframework.transaction.PlatformTransactionManager tm) {
        this.db = db;
        this.ledger = ledger;
        tx = new TransactionTemplate(tm);
    }

    @Scheduled(fixedDelayString = "${finance.reconcile-ms:30000}")
    public void reconcile() {
        var orders =
                db.queryForList(
                        """
SELECT p.order_id FROM payments p LEFT JOIN order_snapshots s ON s.order_id=p.order_id
WHERE p.status='SUCCESS' AND NOT EXISTS(SELECT 1 FROM refunds r WHERE r.order_id=p.order_id)
AND (s.status IN ('CANCELLED','EXPIRED') OR EXISTS(SELECT 1 FROM finance_refund_requests r WHERE r.order_id=p.order_id))
ORDER BY p.id LIMIT 100
""",
                        Long.class);
        for (long order : orders)
            try {
                tx.executeWithoutResult(
                        t -> {
                            ledger.lock("order:" + order);
                            db.update(
                                    """
INSERT INTO finance_refund_requests(order_id,amount,reason,event_id,correlation_id)
SELECT p.order_id,p.amount,'LATE_PAYMENT',?,? FROM payments p JOIN order_snapshots s ON s.order_id=p.order_id
WHERE p.order_id=? AND p.status='SUCCESS' AND s.status IN ('CANCELLED','EXPIRED') ON CONFLICT DO NOTHING
""",
                                    UUID.randomUUID(),
                                    UUID.randomUUID(),
                                    order);
                            ledger.tryRefund(order);
                        });
            } catch (RuntimeException ex) {
                LOG.error(
                        "Refund reconciliation failed for order {} ({})",
                        order,
                        ex.getClass().getSimpleName());
            }
    }
}
