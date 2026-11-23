import { useEffect, useState } from 'react';
import { Alert, Button, Card, Col, Empty, InputNumber, Pagination, Row, Spin, Tag } from 'antd';
import { ArrowLeftOutlined, EnvironmentOutlined, ShopOutlined, StarFilled } from '@ant-design/icons';
import { Link, useParams } from 'react-router-dom';
import { storeApi } from '../../services/storeApi';
import StoreReviews from '../store/StoreReviews';
import '../store/storePages.css';

export default function StoresPage() {
  const [page, setPage] = useState(0);
  const [lat, setLat] = useState(null);
  const [lon, setLon] = useState(null);
  const [radiusKm, setRadiusKm] = useState(null);
  const [filters, setFilters] = useState({});
  const [data, setData] = useState({ content: [], totalElements: 0 });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    setLoading(true); setError('');
    storeApi.stores({ ...filters, page, size: 12 })
      .then((result) => { if (active) setData(result); })
      .catch((failure) => { if (active) setError(failure.message); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [filters, page]);

  function search() {
    if (lat == null && lon == null && radiusKm == null) { setFilters({}); setPage(0); return; }
    if (lat == null || lon == null) { setError('Cần nhập cả vĩ độ và kinh độ'); return; }
    setFilters({ lat, lon, ...(radiusKm != null ? { radiusKm } : {}) }); setPage(0);
  }

  return <main className="store-page"><div className="store-page-heading"><span>ĐỐI TÁC FOODRESCUE</span><h1>Cửa hàng quanh bạn</h1><p>Khám phá các cửa hàng đã được duyệt và đang tham gia giải cứu thực phẩm.</p></div>
    <div className="store-filters"><InputNumber min={-90} max={90} placeholder="Vĩ độ" value={lat} onChange={setLat} /><InputNumber min={-180} max={180} placeholder="Kinh độ" value={lon} onChange={setLon} /><InputNumber min={0.1} max={100} placeholder="Bán kính (km)" value={radiusKm} onChange={setRadiusKm} /><Button type="primary" onClick={search}>Tìm gần tôi</Button><Button onClick={() => { setLat(null); setLon(null); setRadiusKm(null); setFilters({}); setPage(0); }}>Xóa lọc</Button></div>
    {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}
    {loading ? <div className="store-center"><Spin size="large" /></div> : !data.content.length ? <Empty description="Chưa có cửa hàng phù hợp" /> : <><Row gutter={[20, 20]}>{data.content.map((store) => <Col xs={24} sm={12} lg={8} key={store.id}><Link to={`/stores/${store.id}`} className="store-list-link"><Card hoverable className="store-list-card" cover={store.imageUrl ? <img src={store.imageUrl} alt={store.name} /> : <div className="store-list-placeholder"><ShopOutlined /></div>}><h2>{store.name}</h2><p><EnvironmentOutlined /> {store.address}</p><div><Tag color="gold"><StarFilled /> {store.rating ?? 0}</Tag><span>{store.reviewCount} đánh giá</span>{store.distanceKm != null && <span> · {store.distanceKm.toFixed(1)} km</span>}</div></Card></Link></Col>)}</Row><Pagination style={{ marginTop: 24, textAlign: 'center' }} current={page + 1} pageSize={12} total={data.totalElements} showSizeChanger={false} onChange={(current) => setPage(current - 1)} /></>}
  </main>;
}

export function StoreDetailPage() {
  const { id } = useParams();
  const [store, setStore] = useState(null);
  const [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    setStore(null); setError('');
    storeApi.store(id).then((result) => { if (active) setStore(result); })
      .catch((failure) => { if (active) setError(failure.status === 404 ? 'Không tìm thấy cửa hàng đã được duyệt' : failure.message); });
    return () => { active = false; };
  }, [id]);
  return <main className="store-page"><Link to="/stores"><ArrowLeftOutlined /> Tất cả cửa hàng</Link>{error ? <Alert style={{ marginTop: 20 }} type="error" showIcon message={error} /> : !store ? <div className="store-center"><Spin size="large" /></div> : <Card className="store-card store-detail-card">
    {store.imageUrl && <img className="store-detail-image" src={store.imageUrl} alt={store.name} />}
    <div className="store-page-heading"><span>CỬA HÀNG ĐỐI TÁC</span><h1>{store.name}</h1><p>{store.description || 'Cửa hàng đối tác FoodRescue'}</p></div>
    <p><EnvironmentOutlined /> {store.address}</p><p>Giờ mở cửa: {store.openTime?.slice(0, 5)} – {store.closeTime?.slice(0, 5)}</p><p><StarFilled style={{ color: '#d97928' }} /> {store.rating ?? 0} ({store.reviewCount} đánh giá)</p><p>Số điện thoại: {store.phone}</p>
  </Card>}{store && <StoreReviews storeId={store.id} allowCreate />}
  </main>;
}
