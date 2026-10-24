package com.foodrescue.controller.mock;

import com.foodrescue.dto.response.PayoutSummaryResponse;
import com.foodrescue.dto.response.StaffResponse;
import com.foodrescue.dto.response.StatementLineResponse;
import com.foodrescue.dto.response.StockMovementResponse;
import com.foodrescue.dto.response.StoreBatchResponse;
import com.foodrescue.dto.response.StoreResponse;
import com.foodrescue.dto.response.StoreSummaryResponse;
import com.foodrescue.dto.response.UserSummaryResponse;
import com.foodrescue.entity.enums.BatchStatus;
import com.foodrescue.entity.enums.OrderStatus;
import com.foodrescue.entity.enums.PayoutStatus;
import com.foodrescue.entity.enums.Role;
import com.foodrescue.entity.enums.StockReason;
import com.foodrescue.entity.enums.StoreStatus;
import com.foodrescue.entity.enums.UserStatus;
import com.foodrescue.exception.BusinessException;
import com.foodrescue.exception.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Dữ liệu giả của module TV2 (H2), lấy theo dữ liệu mẫu trong foodrescue_schema.sql.
 * Chỉ dùng cho các controller dữ liệu giả; xoá khi nối service thật (S1 ... S13).
 */
public final class StoreMockData {

    private static final LocalDateTime SEED_TIME = LocalDateTime.parse("2026-09-20T08:00:00");

    private StoreMockData() {
    }

    // ---------------------------------------------------------------- cửa hàng
    private static StoreResponse newStore(long id, long ownerId, String ownerName, String ownerEmail, String name,
            String description, String phone, String address, String lat, String lng, String open, String close,
            StoreStatus status, String rate, String bank, String bankNo, String bankHolder) {
        return new StoreResponse(id, ownerId, ownerName, ownerEmail, name, description, phone, address,
                new BigDecimal(lat), new BigDecimal(lng), null, "/uploads/license-" + id + ".jpg",
                LocalTime.parse(open), LocalTime.parse(close), status, null, new BigDecimal(rate),
                bank, bankNo, bankHolder, SEED_TIME);
    }

    public static final List<StoreResponse> STORES = List.of(
            newStore(1, 2, "Nguyễn Văn Phát", "owner.anphat@gmail.com", "Bánh Mì Tươi & Hamburger An Phát",
                    "Bánh mì, sandwich, hamburger làm trong ngày.", "02838300001",
                    "105 An Dương Vương, Phường 8, Quận 5, TP.HCM", "10.75837200", "106.67123900", "06:30:00", "21:30:00",
                    StoreStatus.APPROVED, "15.00", "Vietcombank", "0071000123456", "NGUYEN VAN PHAT"),
            newStore(2, 3, "Lê Thị Thu Thảo", "owner.sweetparis@gmail.com", "Sweet Paris Bakery & Coffee",
                    "Bánh ngọt kiểu Pháp và cà phê.", "02838300002",
                    "45 Lê Duẩn, Bến Nghé, Quận 1, TP.HCM", "10.78125400", "106.69894200", "07:00:00", "22:00:00",
                    StoreStatus.APPROVED, "15.00", "Techcombank", "19031234567890", "LE THI THU THAO"),
            newStore(3, 4, "Trần Văn Cali", "owner.comtam365@gmail.com", "Cơm Tấm Truyền Thống Sài Gòn 365",
                    "Cơm tấm sườn bì chả truyền thống.", "02838300003",
                    "215 Điện Biên Phủ, Phường 15, Bình Thạnh, TP.HCM", "10.79634100", "106.70921800", "10:00:00", "21:00:00",
                    StoreStatus.APPROVED, "15.00", "ACB", "2468101214", "TRAN VAN CALI"),
            newStore(4, 5, "Võ Thị Hạnh", "owner.greenmart@gmail.com", "GreenMart Rau Củ Sạch",
                    "Rau củ quả hữu cơ.", "02838300004",
                    "12 Nguyễn Thị Minh Khai, Đa Kao, Quận 1, TP.HCM", "10.78756000", "106.69980000", "07:00:00", "21:00:00",
                    StoreStatus.PENDING, "10.00", "MB Bank", "0123456789", "VO THI HANH"));

    public static StoreResponse store(Long id) {
        return STORES.stream().filter(s -> s.id().equals(id)).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy cửa hàng"));
    }

    /** Bản sao của cửa hàng với trạng thái / lý do / tỉ lệ phí mới (mô phỏng kết quả thao tác của admin). */
    public static StoreResponse withDecision(StoreResponse s, StoreStatus status, String rejectReason, BigDecimal rate) {
        return new StoreResponse(s.id(), s.ownerId(), s.ownerName(), s.ownerEmail(), s.name(), s.description(), s.phone(),
                s.address(), s.latitude(), s.longitude(), s.imageUrl(), s.licenseImageUrl(), s.openTime(), s.closeTime(),
                status, rejectReason, rate == null ? s.commissionRate() : rate, s.bankName(), s.bankAccountNo(),
                s.bankAccountName(), s.createdAt());
    }

    public static StoreSummaryResponse toSummary(StoreResponse s) {
        return new StoreSummaryResponse(s.id(), s.name(), s.address(), s.phone(), s.latitude(), s.longitude(),
                s.licenseImageUrl(), s.ownerName(), s.ownerEmail(), s.status(), s.rejectReason(), s.commissionRate(),
                s.createdAt());
    }

    // ---------------------------------------------------------------- người dùng
    private static UserSummaryResponse user(long id, Role role, String email, String name, String phone, Long storeId,
            String storeName) {
        return new UserSummaryResponse(id, email, name, phone, role, UserStatus.ACTIVE, storeId, storeName, false, SEED_TIME);
    }

    public static final List<UserSummaryResponse> USERS = List.of(
            user(1, Role.ADMIN, "admin@foodrescue.vn", "Quản Trị Viên", "0909000001", null, null),
            user(2, Role.STORE_OWNER, "owner.anphat@gmail.com", "Nguyễn Văn Phát", "0912345671", null, null),
            user(3, Role.STORE_OWNER, "owner.sweetparis@gmail.com", "Lê Thị Thu Thảo", "0912345672", null, null),
            user(4, Role.STORE_OWNER, "owner.comtam365@gmail.com", "Trần Văn Cali", "0912345673", null, null),
            user(5, Role.STORE_OWNER, "owner.greenmart@gmail.com", "Võ Thị Hạnh", "0912345674", null, null),
            user(6, Role.STORE_STAFF, "staff.anphat@gmail.com", "Đỗ Minh Khoa", "0933000001", 1L, "Bánh Mì Tươi & Hamburger An Phát"),
            user(7, Role.STORE_STAFF, "staff.sweetparis@gmail.com", "Huỳnh Ngọc Mai", "0933000002", 2L, "Sweet Paris Bakery & Coffee"),
            user(8, Role.CUSTOMER, "an.malyhoang@gmail.com", "Ma Lý Hoàng Ân", "0987654321", null, null),
            user(9, Role.CUSTOMER, "linh.tranhoang@gmail.com", "Trần Hoàng Linh", "0987654322", null, null),
            user(10, Role.CUSTOMER, "bao.lequoc@gmail.com", "Lê Quốc Bảo", "0987654323", null, null),
            user(11, Role.CUSTOMER, "thu.phamminh@gmail.com", "Phạm Minh Thư", "0987654324", null, null));

    public static UserSummaryResponse user(Long id) {
        return USERS.stream().filter(u -> u.id().equals(id)).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy người dùng"));
    }

    public static UserSummaryResponse withStatus(UserSummaryResponse u, UserStatus status, boolean mustChangePassword) {
        return new UserSummaryResponse(u.id(), u.email(), u.fullName(), u.phone(), u.role(), status, u.storeId(),
                u.storeName(), mustChangePassword, u.createdAt());
    }

    public static boolean emailExists(String email) {
        return USERS.stream().anyMatch(u -> u.email().equalsIgnoreCase(email));
    }

    // ---------------------------------------------------------------- nhân viên
    public static List<StaffResponse> staffOfStore(Long storeId) {
        return USERS.stream()
                .filter(u -> u.role() == Role.STORE_STAFF && storeId.equals(u.storeId()))
                .map(u -> new StaffResponse(u.id(), u.email(), u.fullName(), u.phone(), u.status(), u.mustChangePassword(),
                        u.createdAt()))
                .toList();
    }

    public static StaffResponse staff(Long storeId, Long staffId) {
        return staffOfStore(storeId).stream().filter(s -> s.id().equals(staffId)).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy nhân viên"));
    }

    public static StaffResponse withStatus(StaffResponse s, UserStatus status) {
        return new StaffResponse(s.id(), s.email(), s.fullName(), s.phone(), status, s.mustChangePassword(), s.createdAt());
    }

    // ---------------------------------------------------------------- túi giải cứu
    private record BatchSeed(long id, long storeId, long categoryId, String categoryName, String title, String description,
            long original, long rescue, int quantity, int remaining, String start, String end, BatchStatus status) {
    }

    private static final List<BatchSeed> BATCH_SEEDS = List.of(
            new BatchSeed(1, 1, 1, "Bánh mì & Sandwich", "Túi Bánh Mì Bất Ngờ",
                    "2 bánh mì hoa cúc mini + 1 sandwich xúc xích trong ngày.", 80000, 30000, 10, 8,
                    "2026-09-25T20:30:00", "2026-09-25T21:30:00", BatchStatus.AVAILABLE),
            new BatchSeed(2, 1, 1, "Bánh mì & Sandwich", "Combo Hamburger Cuối Giờ", "2 burger bò/gà giữ nóng tại quầy.",
                    95000, 40000, 5, 0, "2026-09-23T20:30:00", "2026-09-23T21:30:00", BatchStatus.SOLD_OUT),
            new BatchSeed(3, 2, 2, "Bánh ngọt", "Surprise Box Bánh Pháp",
                    "Ngẫu nhiên 3 loại: croissant phô mai, tart trứng, donut kem.", 120000, 45000, 15, 14,
                    "2026-09-25T21:00:00", "2026-09-25T22:00:00", BatchStatus.AVAILABLE),
            new BatchSeed(4, 2, 2, "Bánh ngọt", "Bánh Kem Lát Mini", "Bánh kem bắp và tiramisu lát, bảo quản tủ mát.",
                    110000, 42000, 8, 6, "2026-09-25T21:00:00", "2026-09-25T22:00:00", BatchStatus.AVAILABLE),
            new BatchSeed(5, 3, 3, "Cơm & Món chính", "Suất Cơm Sườn Bì Chả",
                    "Cơm nóng đóng hộp gồm sườn nướng, bì, chả trứng.", 65000, 25000, 20, 18,
                    "2026-09-25T20:00:00", "2026-09-25T21:00:00", BatchStatus.AVAILABLE));

    public static int discountPercent(BigDecimal original, BigDecimal rescue) {
        return BigDecimal.ONE.subtract(rescue.divide(original, 4, RoundingMode.HALF_UP))
                .multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private static StoreBatchResponse toBatch(BatchSeed b) {
        BigDecimal original = BigDecimal.valueOf(b.original());
        BigDecimal rescue = BigDecimal.valueOf(b.rescue());
        int sold = b.quantity() - b.remaining();
        return new StoreBatchResponse(b.id(), b.categoryId(), b.categoryName(), b.title(), b.description(),
                "/uploads/b" + b.id() + ".jpg", original, rescue, discountPercent(original, rescue), b.quantity(),
                b.remaining(), sold, LocalDateTime.parse(b.start()), LocalDateTime.parse(b.end()), b.status(), sold > 0,
                LocalDateTime.parse(b.start()).minusHours(12));
    }

    public static List<StoreBatchResponse> batchesOfStore(Long storeId, BatchStatus status) {
        return BATCH_SEEDS.stream()
                .filter(b -> b.storeId() == storeId)
                .map(StoreMockData::toBatch)
                .filter(b -> status == null || b.status() == status)
                .toList();
    }

    public static StoreBatchResponse batch(Long storeId, Long batchId) {
        return BATCH_SEEDS.stream()
                .filter(b -> b.storeId() == storeId && b.id() == batchId)
                .map(StoreMockData::toBatch)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "Không tìm thấy túi"));
    }

    /** Bản sao của túi với số lượng mới (mô phỏng thêm / bớt túi). */
    public static StoreBatchResponse withQuantity(StoreBatchResponse b, int quantity, int remaining) {
        BatchStatus status = remaining == 0 ? BatchStatus.SOLD_OUT : BatchStatus.AVAILABLE;
        return new StoreBatchResponse(b.id(), b.categoryId(), b.categoryName(), b.title(), b.description(), b.imageUrl(),
                b.originalPrice(), b.rescuePrice(), b.discountPercent(), quantity, remaining, quantity - remaining,
                b.pickupStart(), b.pickupEnd(), status, quantity - remaining > 0, b.createdAt());
    }

    public static StoreBatchResponse withStatus(StoreBatchResponse b, BatchStatus status) {
        return new StoreBatchResponse(b.id(), b.categoryId(), b.categoryName(), b.title(), b.description(), b.imageUrl(),
                b.originalPrice(), b.rescuePrice(), b.discountPercent(), b.quantity(), b.remainingQuantity(),
                b.soldQuantity(), b.pickupStart(), b.pickupEnd(), status, b.hasOrders(), b.createdAt());
    }

    private static StockMovementResponse move(long id, StockReason reason, int change, int after, String orderCode,
            String actor, String note, String at) {
        return new StockMovementResponse(id, reason, change, after, orderCode, actor, note, LocalDateTime.parse(at));
    }

    /** Sổ kho mẫu theo kịch bản TC23: đăng 10, thêm 2, bớt 1, khách đặt 3, đơn hết hạn trả 3. */
    public static List<StockMovementResponse> stockMovements() {
        return List.of(
                move(1, StockReason.LISTED, 10, 10, null, "Nguyễn Văn Phát", null, "2026-09-25T09:00:00"),
                move(2, StockReason.ADDED, 2, 12, null, "Đỗ Minh Khoa", "Nướng thêm mẻ mới", "2026-09-25T14:00:00"),
                move(3, StockReason.REMOVED, -1, 11, null, "Đỗ Minh Khoa", "Khách mua trực tiếp tại quầy", "2026-09-25T16:30:00"),
                move(4, StockReason.ORDERED, -3, 8, "FR260925-0001", "Ma Lý Hoàng Ân", null, "2026-09-25T18:00:00"),
                move(5, StockReason.RETURNED, 3, 11, "FR260925-0001", null, "Đơn hết hạn thanh toán", "2026-09-25T18:15:00"));
    }

    // ---------------------------------------------------------------- doanh thu, sao kê, chi trả (cửa hàng 3: Cơm Tấm 365)
    private static StatementLineResponse line(long orderId, String code, String completed, long total, long commission,
            Long payoutId, String payoutCode) {
        return new StatementLineResponse(orderId, code, OrderStatus.COMPLETED, LocalDateTime.parse(completed),
                BigDecimal.valueOf(total), new BigDecimal("15.00"), BigDecimal.valueOf(commission),
                BigDecimal.valueOf(total - commission), payoutId, payoutCode);
    }

    public static List<StatementLineResponse> statement() {
        return List.of(
                line(1, "FR260923-0001", "2026-09-23T21:05:00", 100000, 15000, 1L, "PO-3-20260924"),
                line(2, "FR260924-0002", "2026-09-24T20:40:00", 100000, 15000, 1L, "PO-3-20260924"),
                line(5, "FR260925-0005", "2026-09-25T20:30:00", 50000, 7500, null, null));
    }

    public static List<PayoutSummaryResponse> payouts() {
        return List.of(new PayoutSummaryResponse(1L, "PO-3-20260924", LocalDate.parse("2026-09-21"),
                LocalDate.parse("2026-09-24"), 2, BigDecimal.valueOf(200000), BigDecimal.valueOf(30000),
                BigDecimal.valueOf(170000), "ACB", "2468101214", PayoutStatus.PAID, "VCB-FT26267112233",
                LocalDateTime.parse("2026-09-24T22:00:00"), LocalDateTime.parse("2026-09-25T09:30:00")));
    }

    /** Kỳ chi trả mới do cửa hàng bấm "Yêu cầu rút tiền": gom đơn chưa chi (FR260925-0005). */
    public static PayoutSummaryResponse requestedPayout() {
        return new PayoutSummaryResponse(2L, "PO-3-20260925", LocalDate.parse("2026-09-25"), LocalDate.parse("2026-09-25"),
                1, BigDecimal.valueOf(50000), BigDecimal.valueOf(7500), BigDecimal.valueOf(42500), "ACB", "2468101214",
                PayoutStatus.PENDING, null, LocalDateTime.parse("2026-09-25T21:10:00"), null);
    }
}
