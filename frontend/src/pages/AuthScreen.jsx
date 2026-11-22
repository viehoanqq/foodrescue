import { useState } from 'react';
import { Button, Form, Input, message } from 'antd';
import { LockOutlined, ShopOutlined, TeamOutlined } from '@ant-design/icons';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';
import { homeForRole, loginForRole } from '../routes/paths';
import { toFormErrors } from '../services/axios';
import './authScreen.css';

export default function AuthScreen({ mode = 'login', portal }) {
  const [busy, setBusy] = useState(false);
  const [storeRole, setStoreRole] = useState('STORE_OWNER');
  const [form] = Form.useForm();
  const { user: currentUser, authenticate, changePassword } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const isPasswordChange = mode === 'change-password';
  const isRegister = mode === 'register';
  const allowedRoles = portal === 'admin' ? ['ADMIN'] : portal === 'store' ? [storeRole] : ['CUSTOMER'];

  async function submit(values) {
    setBusy(true);
    try {
      const details = { ...values };
      delete details.confirmPassword;
      if (isPasswordChange) {
        const loginPath = loginForRole(currentUser.role);
        await changePassword(details);
        message.success('Đã đổi mật khẩu. Vui lòng đăng nhập lại.');
        navigate(loginPath, { replace: true });
        return;
      }
      const user = await authenticate(details, mode, allowedRoles);
      const destination = user.mustChangePassword ? '/change-password'
        : location.state?.from?.pathname && !isRegister ? location.state.from.pathname : homeForRole(user.role);
      navigate(destination, { replace: true });
    } catch (error) {
      if (error.details?.length) form.setFields(toFormErrors(error));
      message.error(error.message || 'Không thể xác thực');
    } finally {
      setBusy(false);
    }
  }

  return <main className="auth-page">
    <div className="auth-card">
      <Link to="/" className="auth-brand" aria-label="FoodRescue"><img src="/foodrescue-logo.png" alt="FoodRescue" /></Link>
      {isPasswordChange ? <>
        <div className="auth-password-icon"><LockOutlined /></div>
        <h1>Đổi mật khẩu tạm</h1>
        <p className="auth-hint">Đặt mật khẩu mới để tiếp tục sử dụng tài khoản.</p>
      </> : <>
        {!portal && <div className="auth-tabs" role="navigation" aria-label="Đăng nhập hoặc đăng ký">
          <Link to="/login" state={location.state} className={!isRegister ? 'active' : ''}>Đăng nhập</Link>
          <Link to="/register" className={isRegister ? 'active' : ''}>Đăng ký</Link>
        </div>}
        {isRegister && <p className="auth-hint">Tạo tài khoản khách hàng để bắt đầu giải cứu thực phẩm.</p>}
        {portal === 'admin' && <><h1>Đăng nhập quản trị</h1><p className="auth-hint">Truy cập khu vực quản trị FoodRescue.</p></>}
        {portal === 'store' && <>
          <h1>Đăng nhập cửa hàng</h1>
          <div className="auth-section-label">Chọn vai trò của bạn</div>
          <div className="auth-portal-grid auth-store-roles">
            <button type="button" className={`auth-portal ${storeRole === 'STORE_OWNER' ? 'active' : ''}`}
              onClick={() => setStoreRole('STORE_OWNER')}><ShopOutlined /><span>Chủ cửa hàng</span></button>
            <button type="button" className={`auth-portal ${storeRole === 'STORE_STAFF' ? 'active' : ''}`}
              onClick={() => setStoreRole('STORE_STAFF')}><TeamOutlined /><span>Nhân viên</span></button>
          </div>
        </>}
      </>}
      <Form form={form} layout="vertical" onFinish={submit} className="auth-form">
        {!isPasswordChange && <Form.Item name="email" label="Email" rules={[{ required: true, type: 'email' }]}>
          <Input size="large" placeholder="you@example.com" autoComplete="email" />
        </Form.Item>}
        {isRegister && <>
          <Form.Item name="fullName" label="Họ tên" rules={[{ required: true, max: 100 }]}><Input size="large" autoComplete="name" /></Form.Item>
          <Form.Item name="phone" label="Số điện thoại" rules={[{ required: true, max: 20 }]}><Input size="large" autoComplete="tel" /></Form.Item>
        </>}
        {isPasswordChange && <Form.Item name="oldPassword" label="Mật khẩu tạm" rules={[{ required: true }]}>
          <Input.Password size="large" autoComplete="current-password" />
        </Form.Item>}
        <Form.Item name={isPasswordChange ? 'newPassword' : 'password'}
          label={isPasswordChange ? 'Mật khẩu mới' : 'Mật khẩu'}
          rules={[{ required: true, min: isPasswordChange || isRegister ? 8 : 1,
            max: isPasswordChange || isRegister ? 72 : undefined }]}>
          <Input.Password size="large" autoComplete={isRegister || isPasswordChange ? 'new-password' : 'current-password'} />
        </Form.Item>
        {(isRegister || isPasswordChange) && <Form.Item name="confirmPassword" label="Nhập lại mật khẩu"
          dependencies={[isPasswordChange ? 'newPassword' : 'password']}
          rules={[{ required: true, message: 'Nhập lại mật khẩu' }, ({ getFieldValue }) => ({
            validator(_, value) {
              return !value || value === getFieldValue(isPasswordChange ? 'newPassword' : 'password')
                ? Promise.resolve() : Promise.reject(new Error('Hai mật khẩu chưa khớp'));
            },
          })]}>
          <Input.Password size="large" autoComplete="new-password" />
        </Form.Item>}
        <Button type="primary" htmlType="submit" loading={busy} size="large" block className="auth-submit">
          {isPasswordChange ? 'Đổi mật khẩu' : isRegister ? 'Tạo tài khoản' : 'Đăng nhập'}
        </Button>
      </Form>
      <div className="auth-bottom"><Link to="/">Về trang chủ</Link>{portal && <Link to="/login">Đăng nhập khách hàng</Link>}</div>
    </div>
  </main>;
}
