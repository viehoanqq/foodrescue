import { useState } from 'react';
import { Button, Card, Form, Input, Typography, message } from 'antd';
import { useAuth } from '../auth/AuthProvider';
import { toFormErrors } from '../services/axios';

export default function ProfilePage() {
  const { user, updateProfile } = useAuth();
  const [busy, setBusy] = useState(false);
  const [form] = Form.useForm();

  async function submit(values) {
    setBusy(true);
    try {
      await updateProfile(values);
      message.success('Đã cập nhật hồ sơ');
    } catch (error) {
      if (error.details?.length) form.setFields(toFormErrors(error));
      message.error(error.message || 'Không cập nhật được hồ sơ');
    } finally {
      setBusy(false);
    }
  }

  return <Card style={{ maxWidth: 600, margin: '0 auto' }}>
    <Typography.Title level={4}>Hồ sơ của tôi</Typography.Title>
    <Form form={form} layout="vertical" initialValues={{ fullName: user.fullName, phone: user.phone }} onFinish={submit}>
      <Form.Item label="Email"><Input value={user.email} disabled /></Form.Item>
      <Form.Item name="fullName" label="Họ tên" rules={[{ required: true, max: 100 }]}><Input /></Form.Item>
      <Form.Item name="phone" label="Số điện thoại" rules={[{ required: true, max: 20 }]}><Input /></Form.Item>
      <Button type="primary" htmlType="submit" loading={busy}>Lưu thay đổi</Button>
    </Form>
  </Card>;
}
