// Hằng số và hàm kiểm tra toạ độ cho bản đồ chọn vị trí cửa hàng (S0).

/** Tâm bản đồ ban đầu: TP.HCM */
export const HCM_CENTER = { latitude: 10.7769, longitude: 106.7009 };

/** Giới hạn toạ độ lãnh thổ Việt Nam (theo yêu cầu S0) */
export const VN_BOUNDS = { minLat: 8.0, maxLat: 23.5, minLng: 102.0, maxLng: 110.0 };

export const OUT_OF_VIETNAM_MESSAGE = 'Vị trí phải thuộc lãnh thổ Việt Nam';

export function isInVietnam(latitude, longitude) {
  return (
    Number.isFinite(latitude) &&
    Number.isFinite(longitude) &&
    latitude >= VN_BOUNDS.minLat &&
    latitude <= VN_BOUNDS.maxLat &&
    longitude >= VN_BOUNDS.minLng &&
    longitude <= VN_BOUNDS.maxLng
  );
}

/** Làm tròn 6 chữ số thập phân (~0,1 m), nằm gọn trong DECIMAL(10,8) / DECIMAL(11,8) của CSDL */
export function roundCoord(value) {
  return Math.round(value * 1e6) / 1e6;
}
