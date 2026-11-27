import { useEffect, useState } from 'react';
import { Button, Form, Input, Modal, message } from 'antd';
import { LockOutlined, ShopOutlined, TeamOutlined } from '@ant-design/icons';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';
import { homeForRole, loginForRole } from '../routes/paths';
import { toFormErrors } from '../services/axios';
import GoogleSignInButton from '../components/GoogleSignInButton';
import { identityApi } from '../services/identityApi';
import './authScreen.css';

export default function AuthScreen({ mode = 'login', portal }) {
  const [busy, setBusy] = useState(false);
  const [otpSending, setOtpSending] = useState(false);
  const [otpCooldown, setOtpCooldown] = useState(0);
  const [otpEmail, setOtpEmail] = useState(null);
  const [lastOtpEmail, setLastOtpEmail] = useState(null);
  const [registrationDetails, setRegistrationDetails] = useState(null);
  const [storeRole, setStoreRole] = useState('STORE_OWNER');
  const [form] = Form.useForm();
  const { user: currentUser, authenticate, authenticateGoogle, changePassword } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const isPasswordChange = mode === 'change-password';
  const isRegister = mode === 'register';
  const allowedRoles = portal === 'admin' ? ['ADMIN'] : portal === 'store' ? [storeRole] : ['CUSTOMER'];

  useEffect(() => {
    if (otpCooldown <= 0) return undefined;
    const timer = window.setTimeout(() => setOtpCooldown((seconds) => seconds - 1), 1000);
    return () => window.clearTimeout(timer);
  }, [otpCooldown]);

  async function sendRegistrationOtp(email) {
    try {
      setOtpSending(true);
      await identityApi.sendRegistrationOtp(email);
      setOtpCooldown(60);
      setLastOtpEmail(email);
      message.success('Đã gửi mã xác minh đến email của bạn');
      return true;
    } catch (error) {
      message.error(error.message || 'Không gửi được mã xác minh');
      return false;
    } finally {
      setOtpSending(false);
    }
  }

  async function submit(values) {
    setBusy(true);
    try {
      const details = { ...values };
      delete details.confirmPassword;
      if (isRegister && !otpEmail) {
        const email = details.email.trim().toLowerCase();
        if (email !== lastOtpEmail || otpCooldown === 0) {
          const sent = await sendRegistrationOtp(email);
          if (!sent) return;
        }
        setRegistrationDetails({ ...details, email });
        form.setFieldValue('otp', '');
        setOtpEmail(email);
        return;
      }
      if (isPasswordChange) {
        const loginPath = loginForRole(currentUser.role);
        await changePassword(details);
        message.success('Đã đổi mật khẩu. Vui lòng đăng nhập lại.');
        navigate(loginPath, { replace: true });
        return;
      }
      const user = await authenticate(isRegister ? { ...registrationDetails, otp: values.otp } : details,
        mode, allowedRoles);
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

  async function signInWithGoogle(idToken) {
    if (busy) return;
    setBusy(true);
    try {
      const user = await authenticateGoogle(idToken);
      if (!user.phone) message.info('Hãy bổ sung số điện thoại trong hồ sơ để cửa hàng liên hệ khi cần.');
      const destination = !user.phone && user.role === 'CUSTOMER' ? '/me'
        : location.state?.from?.pathname && !isRegister ? location.state.from.pathname : homeForRole(user.role);
      navigate(destination, { replace: true });
    } catch (error) {
      if (error.code === 'GOOGLE_LINK_REQUIRED') {
        Modal.info({ title: 'Email đã có tài khoản',
          content: 'Hãy đăng nhập bằng mật khẩu, mở Hồ sơ cá nhân và chọn Liên kết Google.',
          okText: 'Đã hiểu' });
      } else {
        message.error(error.message || 'Không thể đăng nhập bằng Google');
      }
    } finally {
      setBusy(false);
    }
  }

  return <main className="auth-page">
    <div className="auth-card">
      <Link to="/" className="auth-brand" aria-label="FoodRescue"><img src="/logo.svg" alt="FoodRescue" /></Link>
      {isPasswordChange ? <>
        <div className="auth-password-icon"><LockOutlined /></div>
        <h1>Đổi mật khẩu tạm</h1>
        <p className="auth-hint">Đặt mật khẩu mới để tiếp tục sử dụng tài khoản.</p>
      </> : <>
        {!portal && <div className="auth-tabs" role="navigation" aria-label="Đăng nhập hoặc đăng ký">
          <Link to="/login" state={location.state} className={!isRegister ? 'active' : ''}>Đăng nhập</Link>
          <Link to="/register" className={isRegister ? 'active' : ''}>Đăng ký</Link>
        </div>}
        {isRegister && <p className="auth-hint">{otpEmail
          ? <>Nhập mã xác minh đã gửi đến <strong>{otpEmail}</strong>.</>
          : ''}</p>}
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
        {!isPasswordChange && !otpEmail && <Form.Item name="email" label="Email" rules={[{ required: true, type: 'email' }]}>
          <Input size="large" placeholder="you@example.com" autoComplete="email" />
        </Form.Item>}
        {isRegister && otpEmail && <>
          <Form.Item label="Xác minh email" required>
            <div className="auth-otp-row">
              <Form.Item name="otp" noStyle rules={[{ required: true, message: 'Nhập mã xác minh' },
                { pattern: /^[0-9]{6}$/, message: 'Mã gồm 6 chữ số' }]}>
                <Input size="large" inputMode="numeric" maxLength={6} autoComplete="one-time-code" placeholder="Mã 6 chữ số" />
              </Form.Item>
              <Button size="large" loading={otpSending} disabled={otpCooldown > 0 || busy}
                onClick={() => sendRegistrationOtp(otpEmail)}>
                {otpCooldown > 0 ? `Gửi lại (${otpCooldown}s)` : 'Gửi mã'}
              </Button>
            </div>
          </Form.Item>
        </>}
        {isRegister && !otpEmail && <>
          <Form.Item name="fullName" label="Họ tên" rules={[{ required: true, max: 100 }]}><Input size="large" autoComplete="name" /></Form.Item>
          <Form.Item name="phone" label="Số điện thoại" rules={[{ required: true, max: 20 }]}><Input size="large" autoComplete="tel" /></Form.Item>
        </>}
        {isPasswordChange && <Form.Item name="oldPassword" label="Mật khẩu tạm" rules={[{ required: true }]}>
          <Input.Password size="large" autoComplete="current-password" />
        </Form.Item>}
        {!otpEmail && <Form.Item name={isPasswordChange ? 'newPassword' : 'password'}
          label={isPasswordChange ? 'Mật khẩu mới' : 'Mật khẩu'}
          rules={[{ required: true, min: isPasswordChange || isRegister ? 8 : 1,
            max: isPasswordChange || isRegister ? 72 : undefined }]}>
          <Input.Password size="large" autoComplete={isRegister || isPasswordChange ? 'new-password' : 'current-password'} />
        </Form.Item>}
        {!otpEmail && (isRegister || isPasswordChange) && <Form.Item name="confirmPassword" label="Nhập lại mật khẩu"
          dependencies={[isPasswordChange ? 'newPassword' : 'password']}
          rules={[{ required: true, message: 'Nhập lại mật khẩu' }, ({ getFieldValue }) => ({
            validator(_, value) {
              return !value || value === getFieldValue(isPasswordChange ? 'newPassword' : 'password')
                ? Promise.resolve() : Promise.reject(new Error('Hai mật khẩu chưa khớp'));
            },
          })]}>
          <Input.Password size="large" autoComplete="new-password" />
        </Form.Item>}
        {!isRegister && !isPasswordChange && <div className="auth-forgot-row">
          <Link to="/forgot-password" state={{ portal }}>Quên mật khẩu?</Link>
        </div>}
        <Button type="primary" htmlType="submit" loading={busy} size="large" block className="auth-submit">
          {isPasswordChange ? 'Đổi mật khẩu' : isRegister ? otpEmail ? 'Xác nhận và tạo tài khoản' : 'Tiếp tục' : 'Đăng nhập'}
        </Button>
        {isRegister && otpEmail && <Button block className="auth-back" disabled={busy} onClick={() => {
          setOtpEmail(null);
          form.setFieldValue('otp', '');
        }}>Quay lại sửa thông tin</Button>}
      </Form>
      {!portal && !isPasswordChange && !otpEmail && <div className="auth-google-section">
        <div className="auth-divider"><span>hoặc</span></div>
        <GoogleSignInButton onCredential={signInWithGoogle}
          text={isRegister ? 'signup_with' : 'signin_with'} disabled={busy} />
      </div>}
      <div className="auth-bottom"><Link to="/">Về trang chủ</Link>{portal && <Link to="/login">Đăng nhập khách hàng</Link>}</div>
    </div>
  </main>;
}
