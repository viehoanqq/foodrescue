import StoreBatchesPage from '../pages/store/StoreBatchesPage';
import StoreRegisterPage from '../pages/store/StoreRegisterPage';

/**
 * Route của module cửa hàng (đường dẫn tuyệt đối). `app/routes.jsx` của TV1 chỉ import mảng này,
 * không sửa file của module khác.
 * - /store/register: công khai (chủ cửa hàng chưa có tài khoản)
 * - /store/batches:  khu vực cửa hàng, TV1 bọc PrivateRoute + StoreLayout ở app/routes.jsx
 */
export const storeRoutes = [
  { path: '/store/register', element: <StoreRegisterPage />, public: true },
  { path: '/store/batches', element: <StoreBatchesPage /> },
];
