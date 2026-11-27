package com.foodrescue.finance.application.port.out;

import java.time.LocalDate;
import java.util.Map;

public interface FinanceReports {
    Map<String, Object> list(String resource, Long store, String status, int page, int size);

    Map<String, Object> detail(String resource, long id, Long store);

    Map<String, Object> revenue(Long store, LocalDate from, LocalDate to);

    Map<String, Object> statement(long store, LocalDate from, LocalDate to, int page, int size);

    Map<String, Object> reconciliation();
}
