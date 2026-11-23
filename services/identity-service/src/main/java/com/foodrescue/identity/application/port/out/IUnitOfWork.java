package com.foodrescue.identity.application.port.out;

import java.util.function.Supplier;

public interface IUnitOfWork {
    <T> T execute(Supplier<T> action);
}
