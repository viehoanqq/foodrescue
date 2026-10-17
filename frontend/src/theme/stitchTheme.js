// Design tokens theo bản thiết kế Stitch (bảng màu Primary / Secondary / Tertiary / Neutral, font Plus Jakarta Sans).
// Chỉ áp dụng trong module cửa hàng (qua StoreShell), không sửa shared/theme/theme.js của TV1.

export const S = {
  primary: '#15803D',
  primaryDeep: '#12733A',
  primarySoft: '#DCFCE7',
  secondary: '#EA580C',
  secondarySoft: '#FFE4D6',
  secondaryText: '#C2410C',
  tertiary: '#0284C7',
  neutral: '#64748B',
  navy: '#1B2540',
  text: '#1E293B',
  textMuted: '#64748B',
  pageBg: '#F6F8FF',
  surface: '#FFFFFF',
  soft: '#EEF3FF',
  softBorder: '#DCE6FB',
  infoBg: '#E1EAFF',
  danger: '#DC2626',
  radiusCard: 20,
  radiusControl: 10,
  shadowCard: '0 10px 30px rgba(30, 64, 175, 0.08)',
  font: "'Plus Jakarta Sans', 'Be Vietnam Pro', system-ui, -apple-system, 'Segoe UI', Roboto, Arial, sans-serif",
};

export const stitchTheme = {
  token: {
    colorPrimary: S.primary,
    colorSuccess: S.primary,
    colorWarning: S.secondary,
    colorError: S.danger,
    colorInfo: S.tertiary,
    colorText: S.text,
    colorTextSecondary: S.textMuted,
    colorBgLayout: S.pageBg,
    colorBorder: S.softBorder,
    colorFillTertiary: S.soft, // nền ô nhập kiểu "filled"
    colorFillSecondary: '#E3EBFD',
    fontFamily: S.font,
    fontSize: 14,
    borderRadius: S.radiusControl,
    controlHeight: 44,
  },
  components: {
    Button: { fontWeight: 700, primaryShadow: '0 6px 14px rgba(21, 128, 61, 0.25)' },
    Card: { borderRadiusLG: S.radiusCard },
    Modal: { borderRadiusLG: S.radiusCard },
    Segmented: { trackBg: '#EAF0FF', itemSelectedBg: '#FFFFFF', itemSelectedColor: S.navy },
    Table: { headerBg: '#F1F5FF', headerColor: '#475569', borderColor: '#E6ECFA', rowHoverBg: '#F8FAFF' },
    Tag: { borderRadiusSM: 8 },
  },
};
