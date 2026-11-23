package com.foodrescue.identity.application.port.out;

import com.foodrescue.identity.domain.model.PageResult;
import java.util.Map;
import java.time.LocalDateTime;

public interface IAuditRepository {
    void record(Long actorId, String action, String entityType, Long entityId, String detail);
    PageResult<Map<String, Object>> search(Long actorId, String action, String entityType, Long entityId,
                                           LocalDateTime from, LocalDateTime to, int page, int size);
}
