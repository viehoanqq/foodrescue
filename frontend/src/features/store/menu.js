import { GiftOutlined } from '@ant-design/icons';

/**
 * Menu khu vực cửa hàng (StoreLayout của TV1 đọc mảng này).
 * `icon` là component (không phải element) để file không cần JSX: render bằng <item.icon />.
 * `roles`: vai trò được thấy mục menu.
 */
export const storeMenu = [
  { key: '/store/batches', label: 'Túi giải cứu', icon: GiftOutlined, roles: ['STORE_OWNER', 'STORE_STAFF'] },
];
