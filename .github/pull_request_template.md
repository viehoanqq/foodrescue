## Việc làm
Mã việc (theo bảng phân công): <!-- vd O3, S8 -->
Mô tả ngắn:

## Ảnh chụp màn hình (nếu có giao diện)
<!-- Desktop + điện thoại (375px) -->

## Kiểm tra trước khi xin review
- [ ] Đặt tên đúng quy ước (package, class, hàm, URL, JSON, file React) và dùng đúng thuật ngữ trong từ điển
- [ ] API mới / sửa API: đúng định dạng request/response, lỗi trả `ApiError` với `ErrorCode` có sẵn, Swagger hiển thị đúng
- [ ] Không tự viết nhãn trạng thái, màu, font, cách format tiền / ngày: dùng `status.js`, `theme.js`, `format.js`
- [ ] Không sửa file của module khác; sửa file dùng chung (`common/`, `shared/`, SQL) thì đã báo nhóm và gắn nhãn `shared-change`
- [ ] Đổi số lượng túi chỉ qua `StockService`; huỷ đơn chỉ qua `RefundService`; thao tác tiền / quản trị có ghi `AuditService`
- [ ] Đã test bằng Postman / trên trình duyệt; nếu đụng tới tiền, kho, đơn: đã chạy `foodrescue_checks.sql` và không có dòng lỗi
- [ ] `mvn package` và `npm run build` chạy được; không commit mật khẩu, key, `application-local.yml`, `.env`
