import { useEffect, useState } from 'react';
import { Alert, Button, Card, Form, Input, InputNumber, Typography } from 'antd';
import { EnvironmentOutlined } from '@ant-design/icons';
import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';
import { toFormErrors } from '../services/axios';
import { confirmDialog, toast } from '../components/feedback';
import './profilePage.css';

export default function AddressPage() {
  const { user, updateProfile } = useAuth();
  const [busy, setBusy] = useState(false);
  const [locating, setLocating] = useState(false);
  const [form] = Form.useForm();

  useEffect(() => {
    form.setFieldsValue({
      address: user.address ?? '',
      latitude: user.latitude != null ? Number(user.latitude) : null,
      longitude: user.longitude != null ? Number(user.longitude) : null,
    });
  }, [form, user.address, user.latitude, user.longitude]);

  function locate() {
    if (!navigator.geolocation) {
      toast.error('Trình duyệt không hỗ trợ lấy vị trí');
      return;
    }
    setLocating(true);
    navigator.geolocation.getCurrentPosition(
      (position) => {
        form.setFieldsValue({
          latitude: Number(position.coords.latitude.toFixed(8)),
          longitude: Number(position.coords.longitude.toFixed(8)),
        });
        setLocating(false);
        toast.success('Đã lấy tọa độ hiện tại. Hãy kiểm tra địa chỉ trước khi lưu.');
      },
      () => {
        setLocating(false);
        toast.error('Không lấy được vị trí. Hãy cấp quyền vị trí hoặc nhập tọa độ.');
      },
      { enableHighAccuracy: true, timeout: 12000 },
    );
  }

  async function submit(values) {
    if (!user.phone) {
      toast.warning('Vui lòng bổ sung số điện thoại ở Hồ sơ cá nhân trước khi lưu địa chỉ.');
      return;
    }
    setBusy(true);
    try {
      await updateProfile({
        fullName: user.fullName,
        phone: user.phone,
        address: values.address?.trim() || null,
        latitude: values.latitude ?? null,
        longitude: values.longitude ?? null,
      });
      toast.success('Đã cập nhật địa chỉ');
    } catch (error) {
      if (error.details?.length) form.setFields(toFormErrors(error));
      toast.error(error.message || 'Không cập nhật được địa chỉ');
    } finally {
      setBusy(false);
    }
  }

  function confirmSave(values) {
    const hasLat = values.latitude != null;
    const hasLng = values.longitude != null;
    if (hasLat !== hasLng) {
      toast.error('Cần nhập cả vĩ độ và kinh độ');
      return;
    }
    confirmDialog({
      title: 'Lưu địa chỉ của bạn?',
      content: 'Địa chỉ và tọa độ mới sẽ được dùng để tính khoảng cách đến cửa hàng và điền nhanh khi đặt túi.',
      okText: 'Lưu địa chỉ',
      cancelText: 'Kiểm tra lại',
      tone: 'eco',
      onOk: () => submit(values),
    });
  }

  return (
    <Card className="profile-card">
      <Typography.Title level={4}>Địa chỉ của tôi</Typography.Title>
      <Typography.Paragraph type="secondary">
        Nhập địa chỉ để liên hệ. Lấy vị trí hiện tại hoặc nhập tọa độ để xem khoảng cách đến cửa hàng. Khoảng cách hiển thị là đường thẳng.
      </Typography.Paragraph>

      {!user.phone && (
        <Alert
          type="warning"
          showIcon
          style={{ marginBottom: 18 }}
          message="Chưa có số điện thoại liên hệ"
          description={<>Hãy cập nhật số điện thoại tại <Link to="/me">Hồ sơ cá nhân</Link> trước khi lưu địa chỉ.</>}
        />
      )}

      <Form form={form} layout="vertical" onFinish={confirmSave}>
        <Form.Item name="address" label="Địa chỉ" rules={[{ max: 255 }]}>
          <Input maxLength={255} placeholder="Số nhà, đường, phường, quận, thành phố" />
        </Form.Item>
        <div className="profile-location-fields">
          <Form.Item name="latitude" label="Vĩ độ" rules={[{ type: 'number', min: -90, max: 90 }]}>
            <InputNumber min={-90} max={90} precision={8} placeholder="10.776890" />
          </Form.Item>
          <Form.Item name="longitude" label="Kinh độ" rules={[{ type: 'number', min: -180, max: 180 }]}>
            <InputNumber min={-180} max={180} precision={8} placeholder="106.700806" />
          </Form.Item>
        </div>
        <Button className="profile-locate-button" icon={<EnvironmentOutlined />} loading={locating} onClick={locate}>
          Lấy vị trí hiện tại
        </Button>
        <div className="profile-form-actions">
          <Button type="primary" htmlType="submit" loading={busy} disabled={!user.phone}>
            Lưu địa chỉ
          </Button>
        </div>
      </Form>
    </Card>
  );
}
