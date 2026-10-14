# TV3 - Kiểm tra cổng G0

Ngày kiểm tra: 13/10/2026

- MySQL 8 chạy với CSDL `foodrescuedb` gồm 13 bảng.
- Backend khởi động tại `http://localhost:8080`; `GET /api/ping` trả `status: ok`.
- Frontend khởi động tại `http://localhost:5173`; proxy `GET /api/ping` trả `status: ok`.
- `mvn -B verify`: 14 test thành công.
- `npm run lint`: thành công.
- `npm run build`: thành công.

PR này dùng để xác nhận quy trình nhánh của TV3 và kiểm tra GitHub Actions.
