import { Navigate, Outlet, Route, Routes } from 'react-router-dom';
import AppLayout from '../layouts/AppLayout';
import ProfileLayout from '../layouts/ProfileLayout';
import AuthScreen from '../pages/AuthScreen';
import AdminUsersPage from '../pages/admin/AdminUsersPage';
import AdminAuditLogsPage from '../pages/admin/AdminAuditLogsPage';
import ProfilePage from '../pages/ProfilePage';
import PasswordPage from '../pages/PasswordPage';
import CustomerLanding, { PublicHeader } from '../pages/customer/CustomerLanding';
import { AnonymousRoute, PasswordChangeRoute, ProtectedRoute, RoleRoute } from './RouteGuards';
import { STORE_ROLES } from './paths';
import { DemoAdmin, DemoBatchDetail, DemoBatches, DemoCart, DemoHome, DemoOrderDetail, DemoOrders, DemoPickup, DemoRevenue, DemoReviews, DemoStaff, DemoStoreProfile, DemoStoreRegistration } from '../demo/DemoPages';

export default function AppRoutes() {
  return <Routes>
    <Route path="/" element={<CustomerLanding publicPage />} />
    <Route element={<><PublicHeader /><Outlet /></>}>
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
        </Route>
        <Route element={<RoleRoute roles={['CUSTOMER']} />}>
          <Route path="/customer" element={<CustomerLanding />} />
          <Route path="/customer/batches" element={<DemoHome />} />
          <Route path="/store/register" element={<DemoStoreRegistration />} />
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
          <Route path="/store/profile" element={<DemoStoreProfile />} />
          <Route path="/store/staff" element={<DemoStaff />} />
          <Route path="/store/revenue" element={<DemoRevenue />} />
          <Route path="/store/reviews" element={<DemoReviews />} />
        </Route>
        <Route element={<RoleRoute roles={['ADMIN']} />}>
          <Route path="/admin/users" element={<AdminUsersPage />} />
          <Route path="/admin/audit" element={<AdminAuditLogsPage />} />
          {['dashboard', 'stores', 'categories', 'orders', 'refunds', 'payouts', 'finance'].map(section => <Route key={section} path={`/admin/${section}`} element={<DemoAdmin section={section} />} />)}
        </Route>
      </Route>
    </Route>
    <Route path="*" element={<Navigate to="/" replace />} />
  </Routes>;
}
