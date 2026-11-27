import { useEffect, useState } from 'react';
import { Button, Form, Input, message } from 'antd';
import { LockOutlined } from '@ant-design/icons';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { toFormErrors } from '../services/axios';
import { identityApi } from '../services/identityApi';
import './authScreen.css';

function loginPathForPortal(portal) {
  if (portal === 'admin') return '/admin/login';
  if (portal === 'store') return '/store/login';
  return '/login';
}

export default function ForgotPasswordPage() {
  const [busy, setBusy] = useState(false);
  const [otpSending, setOtpSending] = useState(false);
  const [otpCooldown, setOtpCooldown] = useState(0);
  const [resetEmail, setResetEmail] = useState(null);
  const [lastOtpEmail, setLastOtpEmail] = useState(null);
  const [form] = Form.useForm();
  const navigate = useNavigate();
  const location = useLocation();
  const portal = location.state?.portal || new URLSearchParams(location.search).get('portal');
  const backToLogin = loginPathForPortal(portal);

  useEffect(() => {
    if (otpCooldown <= 0) return undefined;
    const timer = window.setTimeout(() => setOtpCooldown((seconds) => seconds - 1), 1000);
    return () => window.clearTimeout(timer);
  }, [otpCooldown]);

  async function sendResetOtp(email) {
    try {
      setOtpSending(true);
      await identityApi.sendForgotPasswordOtp(email);
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
      if (!resetEmail) {
        const email = values.email.trim().toLowerCase();
        if (email !== lastOtpEmail || otpCooldown === 0) {
          const sent = await sendResetOtp(email);
          if (!sent) return;
        }
        form.setFieldsValue({ otp: '', newPassword: '', confirmPassword: '' });
        setResetEmail(email);
        return;
      }

      await identityApi.resetForgotPassword({
        email: resetEmail,
        otp: values.otp.trim(),
        newPassword: values.newPassword,
      });
      message.success('Đặt lại mật khẩu thành công. Vui lòng đăng nhập lại.');
      navigate(backToLogin, { replace: true });
    } catch (error) {
      if (error.details?.length) form.setFields(toFormErrors(error));
      message.error(error.message || 'Không thể đặt lại mật khẩu');
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="auth-page">
      <div className="auth-card">
        <Link to="/" className="auth-brand" aria-label="FoodRescue">
          <img src="/logo.svg" alt="FoodRescue" />
        </Link>
        <div className="auth-password-icon"><LockOutlined /></div>
        <h1>Quên mật khẩu</h1>
        <p className="auth-hint">
          {resetEmail
            ? <>Nhập mã xác minh đã gửi đến <strong>{resetEmail}</strong> và đặt mật khẩu mới.</>
            : 'Nhập email tài khoản để nhận mã OTP đặt lại mật khẩu.'}
        </p>

        <Form form={form} layout="vertical" onFinish={submit} className="auth-form">
          {!resetEmail && (
            <Form.Item name="email" label="Email" rules={[{ required: true, type: 'email', message: 'Nhập email hợp lệ' }]}>
              <Input size="large" placeholder="you@example.com" autoComplete="email" />
            </Form.Item>
          )}

          {resetEmail && (
            <>
              <Form.Item label="Xác minh email" required>
                <div className="auth-otp-row">
                  <Form.Item
                    name="otp"
                    noStyle
                    rules={[
                      { required: true, message: 'Nhập mã xác minh' },
                      { pattern: /^[0-9]{6}$/, message: 'Mã gồm 6 chữ số' },
                    ]}
                  >
                    <Input size="large" inputMode="numeric" maxLength={6} autoComplete="one-time-code" placeholder="Mã 6 chữ số" />
                  </Form.Item>
                  <Button
                    size="large"
                    loading={otpSending}
                    disabled={otpCooldown > 0 || busy}
                    onClick={() => sendResetOtp(resetEmail)}
                  >
                    {otpCooldown > 0 ? `Gửi lại (${otpCooldown}s)` : 'Gửi lại mã'}
                  </Button>
                </div>
              </Form.Item>

              <Form.Item
                name="newPassword"
                label="Mật khẩu mới"
                rules={[
                  { required: true, message: 'Nhập mật khẩu mới' },
                  { min: 8, message: 'Mật khẩu tối thiểu 8 ký tự' },
                  { max: 72, message: 'Mật khẩu tối đa 72 ký tự' },
                ]}
              >
                <Input.Password size="large" autoComplete="new-password" />
              </Form.Item>

              <Form.Item
                name="confirmPassword"
                label="Nhập lại mật khẩu mới"
                dependencies={['newPassword']}
                rules={[
                  { required: true, message: 'Nhập lại mật khẩu mới' },
                  ({ getFieldValue }) => ({
                    validator(_, value) {
                      return !value || value === getFieldValue('newPassword')
                        ? Promise.resolve()
                        : Promise.reject(new Error('Hai mật khẩu chưa khớp'));
                    },
                  }),
                ]}
              >
                <Input.Password size="large" autoComplete="new-password" />
              </Form.Item>
            </>
          )}

          <Button type="primary" htmlType="submit" loading={busy} size="large" block className="auth-submit">
            {resetEmail ? 'Xác nhận đổi mật khẩu' : 'Gửi mã xác minh'}
          </Button>

          {resetEmail && (
            <Button
              block
              className="auth-back"
              disabled={busy}
              onClick={() => {
                setResetEmail(null);
                form.setFieldsValue({ otp: '', newPassword: '', confirmPassword: '' });
              }}
            >
              Quay lại đổi email
            </Button>
          )}
        </Form>

        <div className="auth-bottom">
          <Link to={backToLogin}>Quay lại đăng nhập</Link>
          <Link to="/">Về trang chủ</Link>
        </div>
      </div>
    </main>
  );
}
