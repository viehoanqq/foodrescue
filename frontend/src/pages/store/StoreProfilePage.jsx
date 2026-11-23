import { useEffect, useState } from 'react';
import { Alert, Card, Descriptions, Image, Spin, Tag } from 'antd';
import { storeApi } from '../../services/storeApi';
import './storePages.css';

const statusText = { PENDING: 'Chờ duyệt', APPROVED: 'Đã duyệt', REJECTED: 'Bị từ chối', LOCKED: 'Đã khóa' };

export default function StoreProfilePage() {
  const [profile, setProfile] = useState(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    let active = true;
    storeApi.profile().then((result) => { if (active) setProfile(result); })
      .catch((failure) => { if (active) setError(failure.message); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);
  return <main className="store-page"><div className="store-page-heading"><span>CỬA HÀNG CỦA BẠN</span><h1>Hồ sơ cửa hàng</h1><p>Thông tin đang lưu trong hệ thống.</p></div>
    {loading ? <div className="store-center"><Spin size="large" /></div> : error ? <Alert type="error" showIcon message={error} /> : <>
      {profile.status !== 'APPROVED' && <Alert style={{ marginBottom: 20 }} type="warning" showIcon message={`Trạng thái: ${statusText[profile.status] || profile.status}`} description={profile.rejectReason || 'Cửa hàng chưa thể bán túi giải cứu.'} />}
      <Card className="store-card" title="Thông tin cửa hàng">
        {profile.imageUrl && <Image src={profile.imageUrl} alt={profile.name} className="store-profile-image" />}
        <Descriptions column={{ xs: 1, sm: 2 }} bordered items={[
          { key: 'name', label: 'Tên cửa hàng', children: profile.name },
          { key: 'status', label: 'Trạng thái', children: <Tag color={profile.status === 'APPROVED' ? 'green' : 'gold'}>{statusText[profile.status] || profile.status}</Tag> },
          { key: 'phone', label: 'Số điện thoại', children: profile.phone },
          { key: 'hours', label: 'Giờ hoạt động', children: `${profile.openTime?.slice(0, 5)} – ${profile.closeTime?.slice(0, 5)}` },
          { key: 'address', label: 'Địa chỉ', span: 2, children: profile.address },
          { key: 'coords', label: 'Tọa độ', children: `${profile.latitude}, ${profile.longitude}` },
          { key: 'rate', label: 'Hoa hồng', children: `${profile.commissionRate}%` },
          { key: 'description', label: 'Giới thiệu', span: 2, children: profile.description || 'Chưa có' },
        ]} />
      </Card>
      <Card className="store-card" title="Tài khoản nhận tiền"><Descriptions column={{ xs: 1, sm: 2 }} bordered items={[
        { key: 'bank', label: 'Ngân hàng', children: profile.bankName },
        { key: 'account', label: 'Số tài khoản', children: profile.bankAccountNoMasked },
        { key: 'holder', label: 'Chủ tài khoản', children: profile.bankAccountName },
      ]} /></Card>
    </>}
  </main>;
}
