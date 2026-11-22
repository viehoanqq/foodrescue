# Frontend

React Router quản lý URL và quyền truy cập. `src/App.jsx` chỉ gắn `AppRoutes`; không giữ trang đang mở bằng state.

```text
src/
  auth/AuthProvider.jsx       # Phiên đăng nhập, gọi GET /api/me khi tải lại
  routes/AppRoutes.jsx        # Khai báo URL và trang
  routes/RouteGuards.jsx      # Bắt buộc đăng nhập, đổi mật khẩu, vai trò
  routes/paths.js            # Trang đầu theo vai trò
  layouts/AppLayout.jsx      # Header, menu và Outlet
  pages/                     # Màn hình theo customer/store/admin
  services/axios.js          # Axios, token và chuẩn hóa lỗi
  services/identityApi.js    # API identity đã triển khai
  demo/data.js               # Dữ liệu mẫu nghiệp vụ trong localStorage
```

Route đăng nhập: `/login` cho khách, `/admin/login` cho quản trị, `/staff/login` cho nhân viên, `/store/login` cho chủ hoặc nhân viên cửa hàng. Tất cả dùng cùng `POST /api/auth/login` của identity-service; màn hình theo khu vực kiểm tra role sau khi đăng nhập và chuyển đến `/admin/users` hoặc `/store/orders`. Nhân viên không tự đăng ký qua trang khách; tài khoản nhân viên phải được tạo bởi nghiệp vụ cửa hàng (API này còn dự kiến).

Route chính khác: `/` là homepage công khai; `/customer` là homepage khi đăng nhập. `/batches` và `/batches/:batchId` cho cả khách chưa đăng nhập xem danh sách/chi tiết túi; thêm giỏ yêu cầu đăng nhập. `/customer/batches` và `/customer/batches/:batchId` giữ đường cũ cho khách đã đăng nhập. Các route còn lại gồm `/register`, `/change-password`, `/me`, `/customer/cart`, `/customer/orders`, `/customer/orders/:orderId`, `/store/orders`, `/store/pickup`, `/store/batches`, `/admin/users`, `/payment/result`. `/store/register` dành cho khách đã đăng nhập và lưu hồ sơ mẫu trong trình duyệt, chưa gửi dữ liệu lên store-service.

Identity chạy qua Gateway: đăng nhập, đăng ký, `GET /api/me`, đổi mật khẩu, quản trị user và audit. Frontend dùng kết quả `/api/me` để xác thực phiên sau khi tải lại trang; thông tin user cũ trong localStorage không được dùng để quyết định quyền. Route guard chỉ bảo vệ giao diện; identity-service tiếp tục kiểm tra JWT và vai trò.

Chạy: `npm run dev`; build: `npm run build`.

## Giao diện dữ liệu mẫu

Các route nghiệp vụ khách hàng, cửa hàng và quản trị mới dùng `src/demo/data.js` và lưu thay đổi trong `localStorage` của trình duyệt. Nút **Đặt lại dữ liệu mẫu** trên trang admin khôi phục dữ liệu ban đầu. Luồng đặt đơn, thanh toán mô phỏng, giao hàng, hủy, hoàn tiền, duyệt cửa hàng, quản lý túi, doanh thu và chi trả đều chỉ thay đổi dữ liệu mẫu; chưa gọi API nghiệp vụ. Đăng nhập, hồ sơ và quản trị tài khoản vẫn dùng identity-service đã có.

Dữ liệu mẫu bám theo quyền sở hữu của năm DB: `stores/reviews`, `categories/rescue_batches`, `orders/order_items/cart_items`, `refunds/payouts`, và `users/audit_logs`. Mỗi đơn thuộc một cửa hàng; giá giải cứu tối đa 60% giá gốc; trừ tồn khi đặt, trả tồn khi hủy; đơn đã trả tiền bị hủy tạo refund chờ xử lý. Bản demo không thay thế kiểm tra transaction, Outbox hoặc các ràng buộc ở DB thật.
