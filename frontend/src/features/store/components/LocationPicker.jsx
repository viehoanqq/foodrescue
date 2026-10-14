import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { MapContainer, Marker, TileLayer, useMap, useMapEvents } from 'react-leaflet';
import L from 'leaflet';
import { App, Button, Typography } from 'antd';
import { AimOutlined } from '@ant-design/icons';
import markerIcon2x from 'leaflet/dist/images/marker-icon-2x.png';
import markerIcon from 'leaflet/dist/images/marker-icon.png';
import markerShadow from 'leaflet/dist/images/marker-shadow.png';
import { S } from '../styles/stitchTheme';
import { HCM_CENTER, OUT_OF_VIETNAM_MESSAGE, isInVietnam, roundCoord } from '../utils/geo';

// Vite không tự tìm ảnh ghim mặc định của Leaflet nên phải khai báo icon thủ công.
const pinIcon = L.icon({
  iconUrl: markerIcon,
  iconRetinaUrl: markerIcon2x,
  shadowUrl: markerShadow,
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  shadowSize: [41, 41],
});

// Nguồn ảnh nền không cần API key. Thử lần lượt: OSM Đức -> OpenStreetMap -> Esri.
// OSM Đức đứng đầu vì một số mạng ở Việt Nam không phân giải được tile.openstreetmap.org.
const TILE_PROVIDERS = [
  {
    url: 'https://tile.openstreetmap.de/{z}/{x}/{y}.png',
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
  },
  {
    url: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
  },
  {
    url: 'https://server.arcgisonline.com/ArcGIS/rest/services/World_Street_Map/MapServer/tile/{z}/{y}/{x}',
    attribution: 'Tiles &copy; Esri',
  },
];
const TILE_ERRORS_BEFORE_FALLBACK = 4;

function ClickHandler({ onPick, disabled }) {
  useMapEvents({
    click(e) {
      if (!disabled) onPick(e.latlng.lat, e.latlng.lng);
    },
  });
  return null;
}

function FlyTo({ target }) {
  const map = useMap();
  useEffect(() => {
    if (target) map.flyTo([target.latitude, target.longitude], Math.max(map.getZoom(), 16));
  }, [target, map]);
  return null;
}

/** Tính lại kích thước bản đồ khi khung chứa đổi cỡ (tránh nền xám khi khởi tạo lúc khung chưa có kích thước). */
function AutoResize() {
  const map = useMap();
  useEffect(() => {
    const container = map.getContainer();
    const refresh = () => map.invalidateSize();
    const timer = setTimeout(refresh, 0);
    if (typeof ResizeObserver === 'undefined') return () => clearTimeout(timer);
    const observer = new ResizeObserver(refresh);
    observer.observe(container);
    return () => {
      clearTimeout(timer);
      observer.disconnect();
    };
  }, [map]);
  return null;
}

function Tiles() {
  const { message } = App.useApp();
  const [index, setIndex] = useState(0);
  const errors = useRef(0);

  const handlers = useMemo(
    () => ({
      tileload() {
        errors.current = 0;
      },
      tileerror() {
        errors.current += 1;
        if (errors.current < TILE_ERRORS_BEFORE_FALLBACK) return;
        errors.current = 0;
        if (index < TILE_PROVIDERS.length - 1) {
          setIndex(index + 1);
          message.warning({ content: 'Không tải được bản đồ nền, đang thử nguồn dự phòng', key: 'tile-fallback' });
        } else {
          message.error({ content: 'Không tải được bản đồ nền, kiểm tra kết nối mạng. Bạn vẫn chọn được vị trí bằng cách bấm vào bản đồ.', key: 'tile-fallback' });
        }
      },
    }),
    [index, message],
  );

  const provider = TILE_PROVIDERS[index];
  return <TileLayer key={index} url={provider.url} attribution={provider.attribution} eventHandlers={handlers} />;
}

/**
 * Chọn vị trí cửa hàng trên bản đồ: bấm vào bản đồ hoặc kéo ghim.
 *
 * Props:
 *  - value:    { latitude, longitude } | null | undefined  (dùng được với <Form.Item name="location">)
 *  - onChange: (coords: { latitude, longitude }) => void   (chỉ gọi khi toạ độ hợp lệ)
 *  - height:   chiều cao bản đồ (px), mặc định 360
 *  - disabled: khoá không cho chọn
 *
 * Toạ độ ngoài Việt Nam (lat 8.0-23.5, lng 102.0-110.0) bị từ chối: hiện thông báo lỗi,
 * ghim quay về vị trí hợp lệ gần nhất.
 */
export default function LocationPicker({ value, onChange, height = 360, disabled = false }) {
  const { message } = App.useApp();
  const [pos, setPos] = useState(value ?? null);
  const [focus, setFocus] = useState(null);
  const [locating, setLocating] = useState(false);
  const markerRef = useRef(null);

  // Đồng bộ khi cha đổi giá trị từ bên ngoài (nạp hồ sơ, form.resetFields...): điều chỉnh state ngay lúc render
  const [prevValue, setPrevValue] = useState(value);
  if (value !== prevValue) {
    setPrevValue(value);
    setPos(value ?? null);
  }

  const commit = useCallback(
    (lat, lng) => {
      if (!isInVietnam(lat, lng)) {
        message.error({ content: OUT_OF_VIETNAM_MESSAGE, key: 'location-out-of-vn' });
        return false;
      }
      const next = { latitude: roundCoord(lat), longitude: roundCoord(lng) };
      setPos(next);
      onChange?.(next);
      return true;
    },
    [message, onChange],
  );

  const markerHandlers = useMemo(
    () => ({
      dragend() {
        const marker = markerRef.current;
        if (!marker) return;
        const { lat, lng } = marker.getLatLng();
        const ok = commit(lat, lng);
        // Thả ngoài Việt Nam: đưa ghim về chỗ cũ
        if (!ok && pos) marker.setLatLng([pos.latitude, pos.longitude]);
      },
    }),
    [commit, pos],
  );

  const locateMe = () => {
    if (!navigator.geolocation) {
      message.warning('Trình duyệt không hỗ trợ định vị, hãy chọn vị trí trên bản đồ');
      return;
    }
    setLocating(true);
    navigator.geolocation.getCurrentPosition(
      ({ coords }) => {
        setLocating(false);
        if (commit(coords.latitude, coords.longitude)) {
          setFocus({ latitude: coords.latitude, longitude: coords.longitude });
        }
      },
      () => {
        setLocating(false);
        message.warning('Không lấy được vị trí hiện tại, hãy chọn trên bản đồ');
      },
      { enableHighAccuracy: true, timeout: 8000 },
    );
  };

  return (
    <div>
      <div
        style={{
          position: 'relative',
          isolation: 'isolate', // giữ các lớp của Leaflet nằm dưới Modal / Drawer của Ant Design
          height,
          border: `1px solid ${S.softBorder}`,
          borderRadius: 16,
          overflow: 'hidden',
          background: S.soft,
        }}
      >
        <MapContainer
          center={[HCM_CENTER.latitude, HCM_CENTER.longitude]}
          zoom={13}
          style={{ height: '100%', width: '100%' }}
        >
          <Tiles />
          <AutoResize />
          <ClickHandler onPick={commit} disabled={disabled} />
          <FlyTo target={focus} />
          {pos && (
            <Marker
              ref={markerRef}
              position={[pos.latitude, pos.longitude]}
              icon={pinIcon}
              draggable={!disabled}
              eventHandlers={markerHandlers}
            />
          )}
        </MapContainer>
      </div>

      <div
        style={{
          display: 'flex',
          flexWrap: 'wrap',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 8,
          marginTop: 8,
        }}
      >
        <Typography.Text style={{ fontSize: 13, color: pos ? S.text : S.textMuted }}>
          {pos
            ? `Vĩ độ ${pos.latitude.toFixed(6)}, kinh độ ${pos.longitude.toFixed(6)}`
            : 'Bấm vào bản đồ hoặc kéo ghim để chọn vị trí cửa hàng'}
        </Typography.Text>
        <Button size="small" icon={<AimOutlined />} loading={locating} disabled={disabled} onClick={locateMe}>
          Dùng vị trí của tôi
        </Button>
      </div>
    </div>
  );
}