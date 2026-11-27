import { useState } from 'react';
import { Button, Card, Form, Input, Modal, Typography, message } from 'antd';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';
import { loginForRole } from '../routes/paths';
import { toFormErrors } from '../services/axios';

export default function PasswordPage() {
  const { user, changePassword } = useAuth();
  const [busy, setBusy] = useState(false);
  const [form] = Form.useForm();
  const navigate = useNavigate();

  async function submit(values) {
    setBusy(true);
    try {
      const loginPath = loginForRole(user.role);
      await changePassword({ oldPassword: values.oldPassword, newPassword: values.newPassword });
      message.success('Đã đổi mật khẩu. Vui lòng đăng nhập lại.');
      navigate(loginPath, { replace: true });
    } catch (error) {
      if (error.details?.length) form.setFields(toFormErrors(error));
      message.error(error.message || 'Không đổi được mật khẩu');
    } finally {
      setBusy(false);
    }
  }

  return <Card style={{ maxWidth: 680, margin: '24px auto' }}>
    <Typography.Title level={4}>Đổi mật khẩu</Typography.Title>
    <Typography.Paragraph type="secondary">Sau khi đổi, bạn sẽ đăng nhập lại bằng mật khẩu mới.</Typography.Paragraph>
    <Form form={form} layout="vertical" onFinish={(values) => Modal.confirm({ title: 'Đổi mật khẩu?',
      content: 'Sau khi đổi mật khẩu, bạn sẽ phải đăng nhập lại.', okText: 'Đổi mật khẩu',
      cancelText: 'Quay lại', onOk: () => submit(values) })}>
      <Form.Item name="oldPassword" label="Mật khẩu hiện tại" rules={[{ required: true, message: 'Nhập mật khẩu hiện tại' }]}>
        <Input.Password autoComplete="current-password" />
      </Form.Item>
      <Form.Item name="newPassword" label="Mật khẩu mới" rules={[{ required: true, min: 8, max: 72 }]}>
        <Input.Password autoComplete="new-password" />
      </Form.Item>
      <Form.Item name="confirmPassword" label="Nhập lại mật khẩu mới" dependencies={['newPassword']}
        rules={[{ required: true, message: 'Nhập lại mật khẩu mới' }, ({ getFieldValue }) => ({
          validator(_, value) {
            return !value || value === getFieldValue('newPassword')
              ? Promise.resolve() : Promise.reject(new Error('Hai mật khẩu chưa khớp'));
          },
        })]}>
        <Input.Password autoComplete="new-password" />
      </Form.Item>
      <Button type="primary" htmlType="submit" loading={busy}>Đổi mật khẩu</Button>
    </Form>
  </Card>;
}
