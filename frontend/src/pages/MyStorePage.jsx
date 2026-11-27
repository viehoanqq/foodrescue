import { useEffect, useState } from 'react';
import { Alert, Button, Card, Descriptions, Spin, Tag, Typography } from 'antd';
import { ShopOutlined } from '@ant-design/icons';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';
import { storeApi } from '../services/storeApi';

const statusText = { PENDING: 'Đang chờ duyệt', APPROVED: 'Đã duyệt', REJECTED: 'Bị từ chối', LOCKED: 'Đã khóa' };
const display = (value) => value == null || value === '' ? 'Chưa cung cấp' : value;

export default function MyStorePage() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [registration, setRegistration] = useState(null);
  const [loading, setLoading] = useState(user.role === 'CUSTOMER');
  const [error, setError] = useState('');

  useEffect(() => {
    if (user.role !== 'CUSTOMER') return;
    let active = true;
    storeApi.registration()
      .then((result) => { if (active) setRegistration(result); })
      .catch((failure) => { if (active && failure.status !== 404) setError(failure.message || 'Không tải được hồ sơ cửa hàng'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [user.role]);

  if (user.role === 'STORE_OWNER') return <Card title="Cửa hàng của tôi">
    <Typography.Paragraph>Quản lý hồ sơ và hoạt động của cửa hàng trong trang dành cho chủ cửa hàng.</Typography.Paragraph>
    <Link to="/store/profile"><Button type="primary">Mở hồ sơ cửa hàng</Button></Link>
  </Card>;

  if (loading) return <Card><Spin /></Card>;
  return <Card title="Cửa hàng của tôi">
    {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}
    {registration ? <>
      <Typography.Title level={5}>{display(registration.name)}</Typography.Title>
      <Tag color={registration.status === 'APPROVED' ? 'green' : registration.status === 'REJECTED' ? 'red' : 'gold'}>
        {statusText[registration.status] || display(registration.status)}
      </Tag>
      <Descriptions column={1} style={{ margin: '20px 0' }} items={[
        { key: 'id', label: 'Mã hồ sơ', children: display(registration.storeId) },
        { key: 'address', label: 'Địa chỉ', children: display(registration.address) },
        { key: 'phone', label: 'Số điện thoại', children: display(registration.phone) },
        { key: 'date', label: 'Ngày nộp', children: registration.createdAt ? new Date(registration.createdAt).toLocaleString('vi-VN') : 'Chưa có' },
      ]} />
      {registration.rejectReason && <Alert type="error" showIcon message="Lý do từ chối" description={registration.rejectReason} style={{ marginBottom: 16 }} />}
      <Link to="/store/register"><Button type="primary">Xem chi tiết hồ sơ</Button></Link>
      {registration.status === 'APPROVED' && <Button style={{ marginLeft: 8 }} onClick={() => { logout(); navigate('/store/login'); }}>Đăng nhập trang quản trị cửa hàng</Button>}
    </> : !error ? <>
      <Typography.Paragraph>Bạn chưa tạo cửa hàng. Gửi hồ sơ để quản trị viên xét duyệt.</Typography.Paragraph>
      <Link to="/store/register"><Button type="primary" icon={<ShopOutlined />}>Tạo cửa hàng</Button></Link>
    </> : null}
  </Card>;
}
