package com.foodrescue.identity.infrastructure.adapter.out.persistence;

import com.foodrescue.identity.application.port.out.IUnitOfWork;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class SpringUnitOfWork implements IUnitOfWork {
    private final TransactionTemplate transactions;

    public SpringUnitOfWork(TransactionTemplate transactions) { this.transactions = transactions; }

    @Override public <T> T execute(Supplier<T> action) {
        return transactions.execute(status -> action.get());
    }
}
