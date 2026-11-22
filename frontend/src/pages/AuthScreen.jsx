import { useState } from 'react';
import { Button, Card, Form, Input, Space, Typography, message } from 'antd';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';
import { homeForRole } from '../routes/paths';
import { toFormErrors } from '../services/axios';

const { Title, Text } = Typography;

const PORTALS = {
  admin: { title: 'Đăng nhập quản trị', roles: ['ADMIN'] },
  staff: { title: 'Đăng nhập nhân viên cửa hàng', roles: ['STORE_STAFF'] },
  store: { title: 'Đăng nhập cửa hàng', roles: ['STORE_OWNER', 'STORE_STAFF'] },
};

export default function AuthScreen({ mode = 'login', portal }) {
  const [busy, setBusy] = useState(false);
  const [form] = Form.useForm();
  const { authenticate, changePassword, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const isPasswordChange = mode === 'change-password';
  const isRegister = mode === 'register';

  async function submit(values) {
    setBusy(true);
    try {
      if (isPasswordChange) {
        await changePassword(values);
        message.success('Đã đổi mật khẩu. Vui lòng đăng nhập lại.');
        navigate('/login', { replace: true });
        return;
      }
      const user = await authenticate(values, mode);
      if (portal && !PORTALS[portal].roles.includes(user.role)) {
        logout();
        message.error('Tài khoản này không có quyền vào khu vực đã chọn.');
        return;
      }
      const destination = user.mustChangePassword ? '/change-password'
        : portal ? homeForRole(user.role) : location.state?.from?.pathname || homeForRole(user.role);
      navigate(destination, { replace: true });
    } catch (error) {
      if (error.details?.length) form.setFields(toFormErrors(error));
      message.error(error.message || 'Không thể xác thực');
    } finally {
      setBusy(false);
    }
  }

  return (
    <div style={{ minHeight: '100vh', display: 'grid', placeItems: 'center', background: '#fff7e6', padding: 24 }}>
      <Card style={{ width: '100%', maxWidth: 420 }}>
        <Space direction="vertical" style={{ width: '100%', marginBottom: 20 }}>
          <Title level={3} style={{ margin: 0 }}>FoodRescue</Title>
          <Text type="secondary">{isPasswordChange ? 'Đổi mật khẩu tạm' : isRegister ? 'Đăng ký khách hàng' : PORTALS[portal]?.title || 'Đăng nhập'}</Text>
        </Space>
        <Form form={form} layout="vertical" onFinish={submit}>
          {!isPasswordChange && <Form.Item name="email" label="Email" rules={[{ required: true, type: 'email' }]}>
            <Input autoComplete="email" />
          </Form.Item>}
          {isRegister && <>
            <Form.Item name="fullName" label="Họ tên" rules={[{ required: true }]}><Input /></Form.Item>
            <Form.Item name="phone" label="Số điện thoại" rules={[{ required: true }]}><Input /></Form.Item>
          </>}
          {isPasswordChange && <Form.Item name="oldPassword" label="Mật khẩu tạm" rules={[{ required: true }]}>
            <Input.Password autoComplete="current-password" />
          </Form.Item>}
          <Form.Item name={isPasswordChange ? 'newPassword' : 'password'}
            label={isPasswordChange ? 'Mật khẩu mới' : 'Mật khẩu'}
            rules={[{ required: true, min: isPasswordChange || isRegister ? 8 : 1 }]}>
            <Input.Password autoComplete={isRegister ? 'new-password' : 'current-password'} />
          </Form.Item>
          <Button type="primary" htmlType="submit" loading={busy} block>
            {isPasswordChange ? 'Đổi mật khẩu' : isRegister ? 'Tạo tài khoản' : 'Đăng nhập'}
          </Button>
        </Form>
        {!isPasswordChange && !portal && <Button type="link" style={{ paddingLeft: 0 }}>
          <Link to={isRegister ? '/login' : '/register'}>
            {isRegister ? 'Đã có tài khoản? Đăng nhập' : 'Chưa có tài khoản? Đăng ký'}
          </Link>
        </Button>}
        {portal && <Link to="/login">Đăng nhập khách hàng</Link>}
      </Card>
    </div>
  );
}
