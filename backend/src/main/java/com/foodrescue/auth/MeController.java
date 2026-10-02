package com.foodrescue.auth;

import com.foodrescue.common.security.AuthUser;
import com.foodrescue.common.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Thông tin người đang đăng nhập. Việc A1 sẽ thêm PUT /api/me và PUT /api/me/password. */
@RestController
@RequestMapping("/api/me")
public class MeController {

    @GetMapping
    public AuthUser me() {
        return CurrentUser.get();
    }
}
