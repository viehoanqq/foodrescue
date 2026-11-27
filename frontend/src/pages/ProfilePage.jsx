import { useEffect, useState } from 'react';
import { Button, Card, Form, Input, Typography } from 'antd';
import { useAuth } from '../auth/AuthProvider';
import { toFormErrors } from '../services/axios';
import GoogleSignInButton from '../components/GoogleSignInButton';
import { confirmDialog, toast } from '../components/feedback';
import './profilePage.css';

export default function ProfilePage() {
  const { user, updateProfile, linkGoogle } = useAuth();
  const [busy, setBusy] = useState(false);
  const [linkPassword, setLinkPassword] = useState('');
  const [linking, setLinking] = useState(false);
  const [form] = Form.useForm();

  useEffect(() => {
    form.setFieldsValue({ fullName: user.fullName, phone: user.phone });
  }, [form, user.fullName, user.phone]);

  async function submit(values) {
    setBusy(true);
    try {
      await updateProfile({
        fullName: values.fullName,
        phone: values.phone,
        address: user.address ?? null,
        latitude: user.latitude ?? null,
        longitude: user.longitude ?? null,
      });
      toast.success('Đã cập nhật hồ sơ');
    } catch (error) {
      if (error.details?.length) form.setFields(toFormErrors(error));
      toast.error(error.message || 'Không cập nhật được hồ sơ');
    } finally {
      setBusy(false);
    }
  }

  function confirmGoogleLink(idToken) {
    if (!linkPassword) {
      toast.warning('Nhập mật khẩu hiện tại trước khi liên kết Google.');
      return;
    }
    confirmDialog({
      title: 'Liên kết tài khoản Google?',
      content: `Sau khi liên kết, bạn có thể dùng Google để đăng nhập vào tài khoản ${user.email}. Email Google phải trùng email này.`,
      okText: 'Liên kết',
      cancelText: 'Hủy',
      tone: 'eco',
      onOk: async () => {
        setLinking(true);
        try {
          await linkGoogle(idToken, linkPassword);
          setLinkPassword('');
          toast.success('Đã liên kết tài khoản Google.');
        } catch (error) {
          toast.error(error.message || 'Không thể liên kết Google.');
          throw error;
        } finally {
          setLinking(false);
        }
      },
    });
  }

  return (
    <div className="profile-stack">
      <Card className="profile-card">
        <Typography.Title level={4}>Hồ sơ cá nhân</Typography.Title>
        <Typography.Paragraph type="secondary">
          Thông tin này được dùng để hiển thị tên và liên hệ với bạn.
        </Typography.Paragraph>
        <Form
          form={form}
          layout="vertical"
          onFinish={(values) =>
            confirmDialog({
              title: 'Lưu thay đổi hồ sơ?',
              content: 'Tên và số điện thoại mới sẽ được cập nhật cho tài khoản và các lần đặt túi tiếp theo.',
              okText: 'Lưu thay đổi',
              cancelText: 'Kiểm tra lại',
              tone: 'eco',
              onOk: () => submit(values),
            })
          }
        >
          <Form.Item label="Email"><Input value={user.email} disabled /></Form.Item>
          <Form.Item name="fullName" label="Họ tên" rules={[{ required: true, max: 100 }]}><Input /></Form.Item>
          <Form.Item name="phone" label="Số điện thoại" rules={[{ required: true, max: 20 }]}><Input /></Form.Item>
          <div className="profile-form-actions">
            <Button type="primary" htmlType="submit" loading={busy}>Lưu thay đổi</Button>
          </div>
        </Form>
      </Card>

      {['CUSTOMER', 'STORE_OWNER'].includes(user.role) && (
        <Card className="profile-card">
          <Typography.Title level={5}>Đăng nhập bằng Google</Typography.Title>
          {user.googleLinked ? (
            <Typography.Paragraph style={{ marginBottom: 0 }}>
              Google đã được liên kết với tài khoản này.
            </Typography.Paragraph>
          ) : (
            <div className="profile-google-box">
              <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
                Nhập mật khẩu hiện tại, sau đó chọn tài khoản Google có cùng email {user.email}.
              </Typography.Paragraph>
              <Input.Password
                value={linkPassword}
                onChange={(event) => setLinkPassword(event.target.value)}
                placeholder="Mật khẩu hiện tại"
                autoComplete="current-password"
              />
              <GoogleSignInButton onCredential={confirmGoogleLink} text="continue_with" disabled={linking} />
            </div>
          )}
        </Card>
      )}
    </div>
  );
}
