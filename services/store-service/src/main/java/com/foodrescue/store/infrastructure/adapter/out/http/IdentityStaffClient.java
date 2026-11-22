package com.foodrescue.store.infrastructure.adapter.out.http;

import com.foodrescue.buildingblocks.http.InternalServiceClient;
import com.foodrescue.buildingblocks.http.InternalServiceException;
import com.foodrescue.store.application.port.out.StaffIdentityPort;
import com.foodrescue.store.domain.exception.StoreException;
import java.net.URI;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

@Component
public class IdentityStaffClient implements StaffIdentityPort {
    private final InternalServiceClient client;

    public IdentityStaffClient(@Value("${app.identity-service-url}") String identityUrl,
                               @Value("${app.internal-api-key}") String internalKey) {
        this.client = new InternalServiceClient(URI.create(identityUrl), internalKey);
    }

    @Override
    public CreatedStaff create(Long actorId, Long storeId, NewStaff staff) {
        try {
            CreatedStaff result = client.post("/internal/staff",
                    new InternalStaffRequest(actorId, storeId, staff.email(), staff.fullName(), staff.phone()),
                    UUID.randomUUID(), CreatedStaff.class);
            if (result == null) throw new StoreException(502, "IDENTITY_ERROR", "Identity không trả thông tin nhân viên");
            return result;
        } catch (InternalServiceException ex) {
            if (ex.status() == 409) {
                throw new StoreException(409, "EMAIL_EXISTS", "Email đã được sử dụng");
            }
            if (ex.status() == 400) {
                throw new StoreException(400, "VALIDATION_ERROR", "Thông tin nhân viên không hợp lệ");
            }
            if (ex.status() == 403 || ex.status() == 404) {
                throw new StoreException(403, "STAFF_CREATION_DENIED", "Không thể tạo nhân viên cho cửa hàng này");
            }
            throw new StoreException(502, "IDENTITY_ERROR", "Identity không xử lý được yêu cầu tạo nhân viên");
        } catch (RestClientException ex) {
            throw new StoreException(503, "IDENTITY_UNAVAILABLE", "Không kết nối được Identity");
        }
    }

    private record InternalStaffRequest(Long actorId, Long storeId, String email, String fullName, String phone) {}
}
