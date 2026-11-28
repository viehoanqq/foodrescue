import { useEffect, useMemo, useState } from 'react';
import { Alert, Empty, Input, InputNumber, Pagination, Rate, Select, Spin, Tag } from 'antd';
import { ArrowLeftOutlined, ClockCircleOutlined, EnvironmentOutlined, ShopOutlined, StarFilled } from '@ant-design/icons';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { useAuth } from '../../auth/AuthProvider';
import { catalogApi } from '../../services/catalogApi';
import { storeApi } from '../../services/storeApi';
import { money } from '../../demo/data';
import './batchCatalog.css';

function imageFor(batch, category) {
  return batch.imageUrl || category?.imageUrl || '/category-meal.svg';
}

function discount(batch) {
  return batch.originalPrice > 0 ? Math.round((1 - batch.rescuePrice / batch.originalPrice) * 100) : 0;
}

function pickupInfo(batch, store) {
  const start = batch.pickupStart;
  const end = batch.pickupEnd;
  if (!start || !end) return 'Thời gian nhận hàng chưa được cập nhật';
  const startDate = start.slice(0, 10);
  const endDate = end.slice(0, 10);
  if (startDate === endDate) {
    return `Nhận ngày ${startDate}, ${start.slice(11, 16)}–${end.slice(11, 16)}`;
  }
  const hours = store?.openTime && store?.closeTime
    ? ` · nhận mỗi ngày ${store.openTime.slice(0, 5)}–${store.closeTime.slice(0, 5)}`
    : '';
  return `Bán từ ${startDate} đến ${endDate}${hours}`;
}

function formatStoreDistance(store) {
  const value = store?.distanceKm;
  if (value == null) return null;
  const distance = value < 1 ? `${Math.round(value * 1000)} m` : `${value.toFixed(1)} km`;
  const mode = store.distanceSource === 'OSRM' ? 'đường đi' : 'đường thẳng';
  const eta = store.travelMinutes == null ? '' : ` · khoảng ${Math.round(store.travelMinutes)} phút`;
  return `${distance} ${mode}${eta}`;
}

function BatchCard({ batch }) {
  const category = batch.category;
  const store = batch.store;
  const distance = formatStoreDistance(store);
  return <article className="batch-card">
    <Link className="batch-card-image" to={`/batches/${batch.id}`}><img src={imageFor(batch, category)} alt={batch.title} />
      <span>Giảm {discount(batch)}%</span>{batch.status === 'SOLD_OUT' && <Tag color="red">Đã hết</Tag>}</Link>
    <div className="batch-card-body"><small>{category?.name || 'Túi giải cứu'}</small><Link to={`/batches/${batch.id}`}><h3>{batch.title}</h3></Link>
      <p><ShopOutlined /> {store?.name || `Cửa hàng #${batch.storeId}`}{distance && <span className="batch-card-distance"> · {distance}</span>}</p>
      <p><ClockCircleOutlined /> {pickupInfo(batch, store)}</p>
      <div className="batch-card-bottom"><span><strong>{money(batch.rescuePrice)}</strong><del>{money(batch.originalPrice)}</del></span>
        <Link className="batch-card-details" to={`/batches/${batch.id}`}>Xem túi</Link></div>
    </div>
  </article>;
}

export default function BatchCatalogPage() {
  const { user } = useAuth();
  const [params] = useSearchParams();
  const [search, setSearch] = useState(params.get('q') || '');
  const [keyword, setKeyword] = useState(params.get('q') || '');
  const [categoryId, setCategoryId] = useState('ALL');
  const [sort, setSort] = useState('newest');
  const [categories, setCategories] = useState([]);
  const [categoryError, setCategoryError] = useState('');
  const [minPrice, setMinPrice] = useState(null);
  const [maxPrice, setMaxPrice] = useState(null);
  const [pickupStartAfter, setPickupStartAfter] = useState('');
  const [pickupEndBefore, setPickupEndBefore] = useState('');
  const [radiusKm, setRadiusKm] = useState('ALL');
  const [page, setPage] = useState(0);
  const [resultState, setResultState] = useState({
    key: null, data: { content: [], totalElements: 0, totalPages: 0 }, error: '',
  });
  const location = useMemo(() => {
    if (user?.latitude == null || user?.longitude == null) return null;
    const lat = Number(user.latitude);
    const lon = Number(user.longitude);
    return Number.isFinite(lat) && Number.isFinite(lon) ? { lat, lon } : null;
  }, [user?.latitude, user?.longitude]);

  const query = useMemo(() => {
    const value = { page, size: 12, sort };
    if (keyword) value.keyword = keyword;
    if (categoryId !== 'ALL') value.categoryId = categoryId;
    if (minPrice != null) value.minPrice = minPrice;
    if (maxPrice != null) value.maxPrice = maxPrice;
    if (pickupStartAfter) value.pickupStartAfter = pickupStartAfter;
    if (pickupEndBefore) value.pickupEndBefore = pickupEndBefore;
    if (location) {
      value.lat = location.lat;
      value.lon = location.lon;
      if (radiusKm !== 'ALL') value.radiusKm = radiusKm;
    }
    return value;
  }, [categoryId, keyword, location, maxPrice, minPrice, page, pickupEndBefore,
    pickupStartAfter, radiusKm, sort]);
  const queryKey = JSON.stringify(query);
  const result = resultState.data;
  const loading = resultState.key !== queryKey;
  const error = resultState.key === queryKey ? resultState.error : categoryError;

  useEffect(() => {
    let active = true;
    catalogApi.publicCategories()
      .then(data => { if (active) setCategories(data); })
      .catch(failure => { if (active) setCategoryError(failure.message); });
    return () => { active = false; };
  }, []);

  useEffect(() => {
    let active = true;
    catalogApi.publicBatches(query)
      .then(data => {
        if (active) setResultState({ key: queryKey, data, error: '' });
      })
      .catch(failure => {
        if (active) setResultState({
          key: queryKey, data: { content: [], totalElements: 0, totalPages: 0 }, error: failure.message,
        });
      });
    return () => { active = false; };
  }, [query, queryKey]);

  const rows = result.content || [];

  function applySearch(value) {
    setKeyword(value.trim());
    setPage(0);
  }

  return <main className="batch-page"><div className="batch-page-intro"><span>TÚI GIẢI CỨU</span><h1>Tìm món ngon gần bạn</h1><p>Thực phẩm còn tốt từ các cửa hàng đối tác, với giá dễ chịu hơn mỗi ngày.</p></div>
    <div className="batch-filters"><Input.Search placeholder="Tìm túi, cửa hàng hoặc danh mục" allowClear value={search}
        onChange={event => setSearch(event.target.value)} onSearch={applySearch} />
      <Select aria-label="Danh mục" value={categoryId} onChange={value => { setCategoryId(value); setPage(0); }}
        options={[{ value: 'ALL', label: 'Tất cả danh mục' }, ...categories.map(row => ({ value: row.id, label: row.name }))]} />
      <InputNumber min={0} value={minPrice} placeholder="Giá từ" onChange={value => { setMinPrice(value); setPage(0); }} />
      <InputNumber min={0} value={maxPrice} placeholder="Đến giá" onChange={value => { setMaxPrice(value); setPage(0); }} />
      <Input aria-label="Nhận từ" type="datetime-local" value={pickupStartAfter}
        onChange={event => { setPickupStartAfter(event.target.value); setPage(0); }} />
      <Input aria-label="Hạn đến" type="datetime-local" value={pickupEndBefore}
        onChange={event => { setPickupEndBefore(event.target.value); setPage(0); }} />
      <Select aria-label="Sắp xếp" value={sort} onChange={setSort} options={[
        { value: 'newest', label: 'Mới đăng' }, { value: 'price_asc', label: 'Giá thấp trước' },
        { value: 'discount_desc', label: 'Giảm nhiều nhất' }, { value: 'ending_soon', label: 'Sắp hết giờ' }]} />
      <Select aria-label="Bán kính" value={radiusKm} disabled={!location} onChange={setRadiusKm}
        options={[{ value: 'ALL', label: 'Mọi khoảng cách' }, ...[1, 3, 5, 10].map(value => ({ value, label: `${value} km` }))]} />
    </div>
    <div className="batch-result"><span>{result.totalElements || 0} túi trong khu vực bán</span>
      {user && !location && <Link to="/me">Lưu vị trí để lọc theo khoảng cách</Link>}</div>
    {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}
    {loading ? <div className="batch-loading"><Spin size="large" /></div>
      : rows.length ? <><div className="batch-grid">{rows.map(batch => <BatchCard key={batch.id} batch={batch} />)}</div>
        <Pagination current={page + 1} pageSize={12} total={result.totalElements} showSizeChanger={false}
          onChange={current => setPage(current - 1)} style={{ marginTop: 24, textAlign: 'center' }} /></>
        : <Empty description="Không tìm thấy túi phù hợp" />}
  </main>;
}

export function BatchDetailPage() {
  const { batchId } = useParams();
  const { user } = useAuth();
  const [batchState, setBatchState] = useState({ id: null, batch: null, error: '' });
  const [extraState, setExtraState] = useState({ batchId: null, reviews: [], related: [], error: '' });
  const location = useMemo(() => {
    if (user?.latitude == null || user?.longitude == null) return {};
    const lat = Number(user.latitude);
    const lon = Number(user.longitude);
    return Number.isFinite(lat) && Number.isFinite(lon) ? { lat, lon } : {};
  }, [user?.latitude, user?.longitude]);
  const currentId = String(batchId);
  const batch = batchState.id === currentId ? batchState.batch : null;
  const loading = batchState.id !== currentId;
  const error = batchState.id === currentId ? batchState.error : '';
  const reviews = extraState.batchId === batch?.id ? extraState.reviews : [];
  const related = extraState.batchId === batch?.id ? extraState.related : [];
  const relatedError = extraState.batchId === batch?.id ? extraState.error : '';

  useEffect(() => {
    let active = true;
    catalogApi.publicBatch(batchId, location)
      .then(value => { if (active) setBatchState({ id: currentId, batch: value, error: '' }); })
      .catch(failure => { if (active) setBatchState({ id: currentId, batch: null, error: failure.message }); });
    return () => { active = false; };
  }, [batchId, currentId, location]);

  useEffect(() => {
    if (!batch) return undefined;
    let active = true;
    Promise.all([
      storeApi.reviews(batch.storeId, { page: 0, size: 6 })
        .then(value => ({ content: value.content || [], error: '' }))
        .catch(failure => ({ content: [], error: failure.message })),
      catalogApi.publicBatches({ storeId: batch.storeId, page: 0, size: 5, sort: 'newest' })
        .then(value => ({ content: (value.content || []).filter(row => row.id !== batch.id).slice(0, 4), error: '' }))
        .catch(failure => ({ content: [], error: failure.message })),
    ]).then(([reviewResult, batchResult]) => {
      if (!active) return;
      setExtraState({
        batchId: batch.id,
        reviews: reviewResult.content,
        related: batchResult.content,
        error: [reviewResult.error, batchResult.error].filter(Boolean).join('; '),
      });
    });
    return () => { active = false; };
  }, [batch]);

  if (loading) return <main className="batch-page"><div className="batch-loading"><Spin size="large" /></div></main>;
  if (error || !batch) return <main className="batch-page"><Alert type="error" showIcon message={error || 'Không tìm thấy túi đang bán'} />
    <Link to="/batches">Về danh sách túi</Link></main>;
  const store = batch.store;
  const category = batch.category;
  const rating = reviews.length ? (reviews.reduce((sum, row) => sum + row.rating, 0) / reviews.length).toFixed(1) : store?.rating || 0;
  const distance = formatStoreDistance(store);
  const soldOut = batch.status === 'SOLD_OUT' || batch.remainingQuantity < 1;

  return <main className="batch-page batch-detail-page"><Link className="batch-back" to="/batches"><ArrowLeftOutlined /> Tất cả túi giải cứu</Link>
    {relatedError && <Alert type="warning" showIcon message={`Một số thông tin cửa hàng chưa tải được: ${relatedError}`} style={{ marginBottom: 16 }} />}
    <section className="batch-detail-hero"><div className="batch-detail-gallery"><img src={imageFor(batch, category)} alt={batch.title} />
      <span>Giảm {discount(batch)}%</span></div>
      <div className="batch-detail-info"><div className="batch-detail-label"><Tag color={soldOut ? 'red' : 'green'}>{soldOut ? 'Đã hết' : category?.name || 'Đang bán'}</Tag><span>#{batch.id}</span></div>
        <h1>{batch.title}</h1><div className="batch-detail-store-line"><Link className="batch-detail-store" to={`/stores/${batch.storeId}`}><ShopOutlined /> {store?.name || `Cửa hàng #${batch.storeId}`}</Link>{distance && <span><EnvironmentOutlined /> {distance} từ bạn</span>}</div>
        <div className="batch-detail-rating"><StarFilled /> <strong>{rating}</strong><span>{store?.reviewCount || reviews.length} đánh giá cửa hàng</span></div>
        <div className="batch-detail-price"><strong>{money(batch.rescuePrice)}</strong><del>{money(batch.originalPrice)}</del><span>Tiết kiệm {discount(batch)}%</span></div>
        <p className="batch-detail-description">{batch.description || `Túi ${category?.name?.toLocaleLowerCase('vi-VN') || 'thực phẩm'} còn tốt từ ${store?.name || 'cửa hàng'}. Nội dung túi có thể thay đổi theo thực phẩm trong ngày.`}</p>
        <div className="batch-detail-facts">        <div><ClockCircleOutlined /><span><b>Giờ nhận / hạn bán</b>{pickupInfo(batch, store)}</span></div>
          <div><EnvironmentOutlined /><span><b>Nhận tại cửa hàng</b>{store?.address || 'Xem địa chỉ cửa hàng'}</span></div>
          <div><ShopOutlined /><span><b>Số lượng còn</b>{batch.remainingQuantity} / {batch.quantity} túi</span></div></div>
        {soldOut && <p className="batch-unavailable">Túi đã hết, hiện không thể đặt thêm.</p>}
      </div></section>
    <section className="batch-detail-section"><div className="batch-section-head"><div><span>TRẢI NGHIỆM KHÁCH HÀNG</span><h2>Đánh giá cửa hàng</h2></div><Link to={`/stores/${batch.storeId}`}>Xem cửa hàng</Link></div>
      {reviews.length > 0 && <div className="batch-review-list">{reviews.map(review => <article key={review.id}>
        <div><Rate disabled value={review.rating} /><span>{review.createdAt || `Đơn #${review.orderId}`}</span></div>
        <p>{review.comment || 'Khách hàng đã đánh giá cửa hàng.'}</p><small>Đơn #{review.orderId}</small>
      </article>)}</div>}
      {reviews.length === 0 && <Empty description="Cửa hàng chưa có đánh giá" />}
    </section>
    <section className="batch-detail-section"><div className="batch-section-head"><div><span>TỪ CÙNG CỬA HÀNG</span><h2>Túi khác của {store?.name || 'cửa hàng'}</h2></div><Link to={`/stores/${batch.storeId}`}>Xem cửa hàng</Link></div>
      {related.length > 0 && <div className="batch-grid">{related.map(row => <BatchCard key={row.id} batch={row} />)}</div>}
      {related.length === 0 && <Empty description="Ch?a c? t?i kh?c ?ang b?n" />}
    </section>
  </main>;
}
