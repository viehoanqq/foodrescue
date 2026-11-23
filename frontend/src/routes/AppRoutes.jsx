import { Navigate, Route, Routes } from 'react-router-dom';
import AppLayout from '../layouts/AppLayout';
import ProfileLayout from '../layouts/ProfileLayout';
import AuthScreen from '../pages/AuthScreen';
import AdminUsersPage from '../pages/admin/AdminUsersPage';
import AdminAuditLogsPage from '../pages/admin/AdminAuditLogsPage';
import AdminStoresPage from '../pages/admin/AdminStoresPage';
import ProfilePage from '../pages/ProfilePage';
import MyStorePage from '../pages/MyStorePage';
import PasswordPage from '../pages/PasswordPage';
import CustomerLanding from '../pages/customer/CustomerLanding';
import StoresPage, { StoreDetailPage } from '../pages/customer/StoresPage';
import StoreRegistrationPage from '../pages/store/StoreRegistrationPage';
import StoreProfilePage from '../pages/store/StoreProfilePage';
import StoreStaffPage from '../pages/store/StoreStaffPage';
import OwnerReviewsPage from '../pages/store/OwnerReviewsPage';
import { AnonymousRoute, PasswordChangeRoute, ProtectedRoute, RoleRoute } from './RouteGuards';
import { STORE_ROLES } from './paths';
import { DemoAdmin, DemoBatchDetail, DemoBatches, DemoCart, DemoHome, DemoOrderDetail, DemoOrders, DemoPickup, DemoRevenue } from '../demo/DemoPages';

export default function AppRoutes() {
  return <Routes>
    <Route path="/customer" element={<Navigate to="/" replace />} />
    <Route element={<AppLayout />}>
      <Route path="/" element={<CustomerLanding />} />
      <Route path="/stores" element={<StoresPage />} />
      <Route path="/stores/:id" element={<StoreDetailPage />} />
      <Route path="/batches" element={<DemoHome />} />
      <Route path="/batches/:batchId" element={<DemoBatchDetail />} />
    </Route>
    <Route path="/payment/result" element={<Navigate to="/customer/orders" replace />} />
    <Route element={<AnonymousRoute />}>
      <Route path="/login" element={<AuthScreen mode="login" />} />
      <Route path="/admin/login" element={<AuthScreen mode="login" portal="admin" />} />
      <Route path="/store/login" element={<AuthScreen mode="login" portal="store" />} />
      <Route path="/register" element={<AuthScreen mode="register" />} />
    </Route>
    <Route element={<PasswordChangeRoute />}>
      <Route path="/change-password" element={<AuthScreen mode="change-password" />} />
    </Route>
    <Route element={<ProtectedRoute />}>
      <Route element={<AppLayout />}>
        <Route element={<ProfileLayout />}>
          <Route path="/me" element={<ProfilePage />} />
          <Route path="/me/password" element={<PasswordPage />} />
          <Route element={<RoleRoute roles={['CUSTOMER', 'STORE_OWNER']} />}>
            <Route path="/me/store" element={<MyStorePage />} />
          </Route>
        </Route>
        <Route element={<RoleRoute roles={['CUSTOMER']} />}>
          <Route path="/customer/batches" element={<DemoHome />} />
          <Route path="/store/register" element={<StoreRegistrationPage />} />
          <Route path="/customer/batches/:batchId" element={<DemoBatchDetail />} />
          <Route path="/customer/cart" element={<DemoCart />} />
          <Route path="/customer/orders" element={<DemoOrders />} />
          <Route path="/customer/orders/:orderId" element={<DemoOrderDetail />} />
        </Route>
        <Route element={<RoleRoute roles={STORE_ROLES} />}>
          <Route path="/store/orders" element={<DemoOrders store />} />
          <Route path="/store/orders/:orderId" element={<DemoOrderDetail store />} />
          <Route path="/store/pickup" element={<DemoPickup />} />
          <Route path="/store/batches" element={<DemoBatches />} />
        </Route>
        <Route element={<RoleRoute roles={['STORE_OWNER']} />}>
          <Route path="/store/profile" element={<StoreProfilePage />} />
          <Route path="/store/staff" element={<StoreStaffPage />} />
          <Route path="/store/revenue" element={<DemoRevenue />} />
          <Route path="/store/reviews" element={<OwnerReviewsPage />} />
        </Route>
        <Route element={<RoleRoute roles={['ADMIN']} />}>
          <Route path="/admin/users" element={<AdminUsersPage />} />
          <Route path="/admin/audit" element={<AdminAuditLogsPage />} />
          <Route path="/admin/stores" element={<AdminStoresPage />} />
          {['dashboard', 'categories', 'orders', 'refunds', 'payouts', 'finance'].map(section => <Route key={section} path={`/admin/${section}`} element={<DemoAdmin section={section} />} />)}
        </Route>
      </Route>
    </Route>
    <Route path="*" element={<Navigate to="/" replace />} />
  </Routes>;
}
