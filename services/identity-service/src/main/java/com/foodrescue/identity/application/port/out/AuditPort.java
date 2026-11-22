package com.foodrescue.identity.application.port.out;

import com.foodrescue.identity.domain.model.PageResult;
import java.util.Map;

public interface AuditPort {
    void record(Long actorId, String action, String entityType, Long entityId, String detail);
    PageResult<Map<String, Object>> search(int page, int size);
}
