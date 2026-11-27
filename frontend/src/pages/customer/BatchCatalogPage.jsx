import { useMemo, useState } from 'react';
import { Button, Empty, Input, InputNumber, Rate, Select, Tag, message } from 'antd';
import { ArrowLeftOutlined, ClockCircleOutlined, EnvironmentOutlined, ShopOutlined, ShoppingCartOutlined, StarFilled } from '@ant-design/icons';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { useAuth } from '../../auth/AuthProvider';
import { money, updateDemo } from '../../demo/data';
import { useDemo } from '../../demo/useDemo';
import { formatDistance, storeDistance } from './distance';
import './batchCatalog.css';

function imageFor(batch, category) {
  return batch.imageUrl || category?.imageUrl || '/category-meal.svg';
}

function discount(batch) {
  return batch.originalPrice > 0 ? Math.round((1 - batch.rescuePrice / batch.originalPrice) * 100) : 0;
}

function addToCart(batch, quantity, data, user, navigate) {
  if (!user) { navigate('/login'); return; }
  if (user.role !== 'CUSTOMER') { message.warning('Chỉ tài khoản khách hàng mới có thể đặt túi'); return; }
  if (batch.status !== 'AVAILABLE' || batch.remaining < 1) { message.warning('Túi hiện không còn bán'); return; }
  const inCart = data.cart.find(item => item.batchId === batch.id)?.quantity || 0;
  if (inCart + quantity > Math.min(5, batch.remaining)) { message.warning('Số lượng trong giỏ vượt quá số túi có thể đặt'); return; }
  const otherStore = data.cart.some(item => data.batches.find(row => row.id === item.batchId)?.storeId !== batch.storeId);
  if (otherStore) { message.warning('Mỗi đơn chỉ gồm túi từ một cửa hàng. Hãy hoàn tất giỏ hiện tại trước.'); return; }
  updateDemo(next => {
    const row = next.cart.find(item => item.batchId === batch.id);
    if (row) row.quantity += quantity;
    else next.cart.push({ batchId: batch.id, quantity });
  });
  message.success('Đã thêm túi vào giỏ hàng');
}

function BatchCard({ batch, data }) {
  const { user } = useAuth();
  const navigate = useNavigate();
  const category = data.categories.find(row => row.id === batch.categoryId && row.storeId === batch.storeId);
  const store = data.stores.find(row => row.id === batch.storeId);
  const distance = formatDistance(storeDistance(user, store));
  return <article className="batch-card">
    <Link className="batch-card-image" to={`/batches/${batch.id}`}><img src={imageFor(batch, category)} alt={batch.title} /><span>Giảm {discount(batch)}%</span></Link>
    <div className="batch-card-body"><small>{category?.name || 'Túi giải cứu'}</small><Link to={`/batches/${batch.id}`}><h3>{batch.title}</h3></Link>
      <p><ShopOutlined /> {store?.name || `Cửa hàng #${batch.storeId}`}{distance && <span className="batch-card-distance"> · {distance}</span>}</p>
      <p><ClockCircleOutlined /> Nhận {batch.pickup}</p>
      <div className="batch-card-bottom"><span><strong>{money(batch.rescuePrice)}</strong><del>{money(batch.originalPrice)}</del></span>
        <Button type="primary" shape="circle" icon={<ShoppingCartOutlined />} aria-label={`Thêm ${batch.title} vào giỏ`} onClick={() => addToCart(batch, 1, data, user, navigate)} /></div>
    </div>
  </article>;
}

export default function BatchCatalogPage() {
  const data = useDemo();
  const { user } = useAuth();
  const [params] = useSearchParams();
  const [search, setSearch] = useState(params.get('q') || '');
  const [category, setCategory] = useState('ALL');
  const [sort, setSort] = useState('new');
  const categories = data.categories.filter(row => data.stores.some(store => store.id === row.storeId && store.status === 'APPROVED'));
  const rows = useMemo(() => data.batches.filter(batch => {
    const store = data.stores.find(row => row.id === batch.storeId);
    const group = data.categories.find(row => row.id === batch.categoryId && row.storeId === batch.storeId);
    const query = search.trim().toLocaleLowerCase('vi-VN');
    return batch.status === 'AVAILABLE' && batch.remaining > 0 && store?.status === 'APPROVED'
      && (category === 'ALL' || group?.name === category)
      && (!query || `${batch.title} ${batch.description || ''} ${store.name} ${group?.name || ''}`.toLocaleLowerCase('vi-VN').includes(query));
  }).sort((a, b) => sort === 'price' ? a.rescuePrice - b.rescuePrice : sort === 'discount' ? discount(b) - discount(a) : b.id - a.id), [data, search, category, sort]);

  return <main className="batch-page"><div className="batch-page-intro"><span>TÚI GIẢI CỨU</span><h1>Tìm món ngon gần bạn</h1><p>Thực phẩm còn tốt từ các cửa hàng đối tác, với giá dễ chịu hơn mỗi ngày.</p></div>
    <div className="batch-filters"><Input.Search placeholder="Tìm túi, cửa hàng hoặc danh mục" allowClear value={search} onChange={event => setSearch(event.target.value)} />
      <Select aria-label="Danh mục" value={category} onChange={setCategory} options={[{ value: 'ALL', label: 'Tất cả danh mục' }, ...[...new Set(categories.map(row => row.name))].map(name => ({ value: name, label: name }))]} />
      <Select aria-label="Sắp xếp" value={sort} onChange={setSort} options={[{ value: 'new', label: 'Mới đăng' }, { value: 'price', label: 'Giá thấp trước' }, { value: 'discount', label: 'Giảm nhiều nhất' }]} />
    </div><div className="batch-result"><span>{rows.length} túi đang bán</span>{user && user.latitude == null && <Link to="/me">Lưu vị trí để xem khoảng cách</Link>}</div>
    {rows.length ? <div className="batch-grid">{rows.map(batch => <BatchCard key={batch.id} batch={batch} data={data} />)}</div> : <Empty description="Không tìm thấy túi phù hợp" />}
  </main>;
}

export function BatchDetailPage() {
  const { batchId } = useParams();
  const data = useDemo();
  const { user } = useAuth();
  const navigate = useNavigate();
  const [quantity, setQuantity] = useState(1);
  const batch = data.batches.find(row => row.id === Number(batchId));
  if (!batch) return <main className="batch-page"><Empty description="Không tìm thấy túi giải cứu" /><Link to="/batches">Về danh sách túi</Link></main>;
  const store = data.stores.find(row => row.id === batch.storeId);
  const distance = formatDistance(storeDistance(user, store));
  const category = data.categories.find(row => row.id === batch.categoryId && row.storeId === batch.storeId);
  const reviews = data.reviews.filter(row => row.storeId === batch.storeId);
  const rating = reviews.length ? (reviews.reduce((sum, row) => sum + row.rating, 0) / reviews.length).toFixed(1) : store?.rating || 0;
  const related = data.batches.filter(row => row.id !== batch.id && row.storeId === batch.storeId && row.status === 'AVAILABLE' && row.remaining > 0).slice(0, 4);
  const canBuy = batch.status === 'AVAILABLE' && batch.remaining > 0 && store?.status === 'APPROVED';

  return <main className="batch-page batch-detail-page"><Link className="batch-back" to="/batches"><ArrowLeftOutlined /> Tất cả túi giải cứu</Link>
    <section className="batch-detail-hero"><div className="batch-detail-gallery"><img src={imageFor(batch, category)} alt={batch.title} /><span>Giảm {discount(batch)}%</span></div>
      <div className="batch-detail-info"><div className="batch-detail-label"><Tag color="green">{category?.name || 'Túi giải cứu'}</Tag><span>#{batch.id}</span></div>
        <h1>{batch.title}</h1><div className="batch-detail-store-line"><Link className="batch-detail-store" to={`/stores/${batch.storeId}`}><ShopOutlined /> {store?.name || `Cửa hàng #${batch.storeId}`}</Link>{distance && <span><EnvironmentOutlined /> {distance} từ bạn</span>}</div>
        <div className="batch-detail-rating"><StarFilled /> <strong>{rating}</strong><span>{reviews.length} đánh giá cửa hàng</span></div>
        <div className="batch-detail-price"><strong>{money(batch.rescuePrice)}</strong><del>{money(batch.originalPrice)}</del><span>Tiết kiệm {discount(batch)}%</span></div>
        <p className="batch-detail-description">{batch.description || `Túi ${category?.name?.toLocaleLowerCase('vi-VN') || 'thực phẩm'} còn tốt từ ${store?.name || 'cửa hàng'}. Nội dung túi có thể thay đổi theo thực phẩm trong ngày.`}</p>
        <div className="batch-detail-facts"><div><ClockCircleOutlined /><span><b>Giờ nhận</b>{batch.pickup}</span></div>
          <div><EnvironmentOutlined /><span><b>Nhận tại cửa hàng</b>{store?.address || 'Xem địa chỉ cửa hàng'}</span></div>
          <div><ShoppingCartOutlined /><span><b>Số lượng còn</b>{batch.remaining} / {batch.quantity} túi</span></div></div>
        <div className="batch-detail-action"><InputNumber aria-label="Số lượng" min={1} max={Math.min(5, Math.max(1, batch.remaining))} value={quantity} onChange={value => setQuantity(value || 1)} disabled={!canBuy} />
          <Button size="large" type="primary" icon={<ShoppingCartOutlined />} disabled={!canBuy} onClick={() => addToCart(batch, quantity, data, user, navigate)}>Thêm vào giỏ</Button></div>
        {!canBuy && <p className="batch-unavailable">Túi hiện không còn bán.</p>}
      </div></section>
    <section className="batch-detail-section"><div className="batch-section-head"><div><span>TRẢI NGHIỆM KHÁCH HÀNG</span><h2>Đánh giá cửa hàng</h2></div><Link to={`/stores/${batch.storeId}`}>Xem cửa hàng</Link></div>
      {reviews.length ? <div className="batch-review-list">{reviews.map(review => <article key={review.id}><div><Rate disabled value={review.rating} /><span>{review.createdAt || `Đơn #${review.orderId}`}</span></div><p>{review.comment || 'Khách hàng đã đánh giá cửa hàng.'}</p><small>Đơn #{review.orderId}</small></article>)}</div> : <Empty description="Cửa hàng chưa có đánh giá" />}
    </section>
    <section className="batch-detail-section"><div className="batch-section-head"><div><span>TỪ CÙNG CỬA HÀNG</span><h2>Túi khác của {store?.name || 'cửa hàng'}</h2></div><Link to={`/stores/${batch.storeId}`}>Xem tất cả</Link></div>
      {related.length ? <div className="batch-grid">{related.map(row => <BatchCard key={row.id} batch={row} data={data} />)}</div> : <Empty description="Chưa có túi khác đang bán" />}
    </section>
  </main>;
}
