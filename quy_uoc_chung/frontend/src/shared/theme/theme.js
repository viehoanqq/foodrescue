// ============================================================================
//  FoodRescue - Design tokens (NGUỒN DUY NHẤT cho màu, font, bo góc, khoảng cách)
//  Phong cách tham khảo trang đặt món Capichi: nền trắng, cam chủ đạo, nút bo tròn,
//  thẻ ảnh nền xám nhạt. Thương hiệu, logo, ảnh là của FoodRescue.
//  Không viết mã màu / cỡ chữ trực tiếp trong component: luôn lấy từ file này.
// ============================================================================

export const colors = {
  primary: '#FF7A1A', // cam chủ đạo: nút chính, link, giá giải cứu, tab đang chọn
  primaryHover: '#F06A0A',
  primarySoft: '#FFF1E6', // nền nhạt của cam: badge, ô được chọn
  dark: '#111111', // nút phụ nền đen ("Đăng ký"), tiêu đề lớn
  eco: '#1E8E4E', // xanh "giải cứu": nhãn tiết kiệm, CO2, trạng thái hoàn tất
  ecoSoft: '#E8F5EE',
  danger: '#E5484D',
  info: '#2C7BE5',
  text: '#1F1F1F',
  textSecondary: '#6B7280',
  textMuted: '#9CA3AF', // giá gốc gạch ngang, chú thích
  border: '#E5E7EB',
  divider: '#EEEEEE',
  bg: '#FFFFFF',
  bgSoft: '#F5F5F5', // nền vùng ảnh của thẻ danh mục / thẻ túi
  bgPage: '#FAFAFA', // nền trang quản trị (cửa hàng, admin)
};

export const font = {
  family: "'Be Vietnam Pro', system-ui, -apple-system, 'Segoe UI', Roboto, Arial, sans-serif",
  mono: "ui-monospace, 'SFMono-Regular', Consolas, monospace", // mã đơn, mã nhận hàng
  size: { xs: 12, sm: 13, base: 14, md: 16, lg: 18, xl: 22, xxl: 28 },
  weight: { regular: 400, medium: 500, semibold: 600, bold: 700 },
};

export const space = { xs: 4, sm: 8, md: 12, lg: 16, xl: 24, xxl: 32, xxxl: 48 };

export const radius = {
  sm: 6,
  md: 8, // ô nhập, nút vuông
  lg: 12, // thẻ (card), banner
  pill: 999, // ô tìm kiếm, nút "Đăng ký", tab dạng viên thuốc
};

export const shadow = {
  card: '0 2px 10px rgba(0, 0, 0, 0.06)',
  cardHover: '0 6px 18px rgba(0, 0, 0, 0.10)',
  header: '0 1px 0 #EEEEEE',
};

export const layout = {
  contentWidth: 1200, // chiều rộng tối đa vùng nội dung trang khách hàng
  headerHeight: 80,
  siderWidth: 232, // menu trái trang cửa hàng / admin
  gutter: 16, // lề hai bên trên điện thoại
};

// Cấu hình Ant Design 5: <ConfigProvider theme={antdTheme} locale={viVN}>
export const antdTheme = {
  token: {
    colorPrimary: colors.primary,
    colorSuccess: colors.eco,
    colorWarning: '#F5A524',
    colorError: colors.danger,
    colorInfo: colors.info,
    colorText: colors.text,
    colorTextSecondary: colors.textSecondary,
    colorBorder: colors.border,
    colorBgLayout: colors.bgPage,
    fontFamily: font.family,
    fontSize: font.size.base,
    borderRadius: radius.md,
    controlHeight: 40,
  },
  components: {
    Button: { fontWeight: font.weight.semibold, primaryShadow: 'none' },
    Input: { controlHeightLG: 52 },
    Card: { borderRadiusLG: radius.lg },
    Layout: { headerBg: colors.bg, siderBg: colors.bg, bodyBg: colors.bgPage },
    Menu: { itemSelectedBg: colors.primarySoft, itemSelectedColor: colors.primary },
    Segmented: { itemSelectedBg: colors.primary, itemSelectedColor: '#FFFFFF' },
    Tag: { borderRadiusSM: radius.sm },
  },
};
