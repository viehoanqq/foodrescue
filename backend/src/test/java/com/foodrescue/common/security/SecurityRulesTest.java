package com.foodrescue.common.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kiểm tra quy tắc bảo mật F4 trên dữ liệu mẫu (cần MySQL có foodrescuedb).
 * Id mẫu: 1 admin, 2 chủ An Phát (cửa hàng 1), 6 nhân viên An Phát, 8 khách hàng.
 * @Transactional: mọi thay đổi trong test được hoàn tác sau khi chạy.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.dev-auth.enabled=true")
@Transactional
class SecurityRulesTest {

    @Autowired MockMvc mvc;
    @Autowired JwtService jwtService;
    @Autowired JdbcTemplate jdbc;

    @Test
    void apiCongKhai_khongCanDangNhap() throws Exception {
        mvc.perform(get("/api/ping")).andExpect(status().isOk());
    }

    @Test
    void chuaDangNhap_bi401() throws Exception {
        mvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void khachGoiApiAdmin_bi403() throws Exception {
        mvc.perform(get("/api/admin/stores").header("X-Dev-User-Id", "8"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void adminQuaDuocLopQuyen_urlChuaCo_bi404() throws Exception {
        mvc.perform(get("/api/admin/khong-co").header("X-Dev-User-Id", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void devHeader_nhanVienCoStoreId() throws Exception {
        mvc.perform(get("/api/me").header("X-Dev-User-Id", "6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("STORE_STAFF"))
                .andExpect(jsonPath("$.storeId").value(1));
    }

    @Test
    void bearerToken_chuCuaHangCoStoreId() throws Exception {
        String token = jwtService.generateToken(2L);
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("STORE_OWNER"))
                .andExpect(jsonPath("$.storeId").value(1));
    }

    @Test
    void tokenSai_bi401() throws Exception {
        mvc.perform(get("/api/me").header("Authorization", "Bearer abc.def.ghi"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void taiKhoanBiKhoa_bi403() throws Exception {
        jdbc.update("UPDATE users SET status = 'LOCKED' WHERE id = 8");
        mvc.perform(get("/api/me").header("X-Dev-User-Id", "8"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    void matKhauTam_chiDuocXemHoSo() throws Exception {
        jdbc.update("UPDATE users SET must_change_password = 1 WHERE id = 8");
        mvc.perform(get("/api/orders").header("X-Dev-User-Id", "8"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        mvc.perform(get("/api/me").header("X-Dev-User-Id", "8"))
                .andExpect(status().isOk());
    }
}
