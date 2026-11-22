import { Button, Input, Space, Typography } from 'antd';
import { ArrowRightOutlined, EnvironmentOutlined, GiftOutlined, HeartOutlined, SearchOutlined, ShopOutlined, ShoppingOutlined } from '@ant-design/icons';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../auth/AuthProvider';
import { useDemo } from '../../demo/useDemo';
import { money } from '../../demo/data';
import './customerLanding.css';

const { Title, Paragraph, Text } = Typography;
const productPath = '/batches';

function ProductCard({ batch, store, compact = false }) {
  return <Link className={`home-product ${compact ? 'home-product-compact' : ''}`} to={`/batches/${batch.id}`}>
    <div className="home-product-media"><GiftOutlined /><span>{Math.round((1 - batch.rescuePrice / batch.originalPrice) * 100)}% giảm</span></div>
    <div className="home-product-copy"><Text className="home-product-store">{store?.name}</Text><h3>{batch.title}</h3><p><EnvironmentOutlined /> {store?.address}</p><div className="home-product-bottom"><strong>{money(batch.rescuePrice)}</strong><del>{money(batch.originalPrice)}</del></div></div>
  </Link>;
}

export function PublicHeader() {
  return <header className="home-public-header"><div className="home-wrap"><Link to="/" className="home-brand" aria-label="FoodRescue"><img src="/foodrescue-logo.png" alt="FoodRescue" /></Link><nav><Link to="/">Trang chủ</Link><Link to="/batches">Túi giải cứu</Link><Link to="/login">Đăng nhập</Link><Link to="/register" className="home-register">Đăng ký</Link></nav></div></header>;
}

export default function CustomerLanding({ publicPage = false }) {
  const data = useDemo(); const navigate = useNavigate(); const { user } = useAuth();
  const available = data.batches.filter(b => b.status === 'AVAILABLE' && b.remaining > 0 && data.stores.some(s => s.id === b.storeId && s.status === 'APPROVED'));
  const newest = [...available].sort((a, b) => b.id - a.id).slice(0, 3);
  const popular = [...available].sort((a, b) => (b.quantity - b.remaining) - (a.quantity - a.remaining)).slice(0, 3);
  const catalog = productPath;
  return <div className={publicPage ? 'home-public' : ''}>
    {publicPage && <PublicHeader />}
    <div className="home-wrap">
      <section className="home-hero"><div className="home-hero-copy"><span className="home-eyebrow">THỰC PHẨM NGON, GIÁ TỐT MỖI NGÀY</span><Title>Giải cứu bữa ngon<br/><em>ngay gần bạn.</em></Title><Paragraph>Chọn những túi thực phẩm cuối ngày từ cửa hàng địa phương, tiết kiệm chi phí và cùng giảm lãng phí thực phẩm.</Paragraph><Space wrap><Button type="primary" size="large" icon={<ShoppingOutlined />} onClick={() => navigate(catalog)}>Khám phá túi giải cứu</Button><Button size="large" onClick={() => document.getElementById('how-it-works')?.scrollIntoView({ behavior: 'smooth' })}>Cách hoạt động</Button></Space><div className="home-hero-meta"><span><ShopOutlined /> Cửa hàng địa phương</span><span><HeartOutlined /> Tiết kiệm đến 60%</span></div></div><div className="home-hero-art" aria-hidden="true"><div className="home-art-circle"><GiftOutlined /></div><div className="home-art-note">Túi ngon cuối ngày<br/><strong>từ 30.000 đ</strong></div></div></section>
      <section className="home-search"><div><strong>Hôm nay bạn muốn ăn gì?</strong><p>Tìm túi phù hợp trong danh sách cửa hàng đang bán.</p></div><Input.Search placeholder="Tìm bánh mì, bánh ngọt, bữa tối..." prefix={<SearchOutlined />} size="large" onSearch={value => navigate(value ? `${catalog}?q=${encodeURIComponent(value)}` : catalog)} enterButton="Tìm túi" /></section>
      <section className="home-section"><div className="home-section-heading"><div><span className="home-eyebrow">VỪA LÊN KỆ</span><h2>Túi mới đăng</h2><p>Những lựa chọn mới từ cửa hàng gần bạn.</p></div><Link to={catalog}>Xem tất cả <ArrowRightOutlined /></Link></div><div className="home-product-grid">{newest.map(b => <ProductCard key={b.id} batch={b} store={data.stores.find(s => s.id === b.storeId)} />)}</div></section>
      <section className="home-promo"><div><span className="home-eyebrow">CÙNG GIẢM LÃNG PHÍ</span><h2>Một túi nhỏ, nhiều điều tốt.</h2><p>Thực phẩm còn ngon được đưa đến đúng người cần, với mức giá dễ tiếp cận hơn.</p><Link to={catalog}>Chọn túi hôm nay <ArrowRightOutlined /></Link></div><div className="home-promo-mark"><HeartOutlined /></div></section>
      <section className="home-section"><div className="home-section-heading"><div><span className="home-eyebrow">ĐƯỢC QUAN TÂM</span><h2>Túi bán chạy</h2><p>Những túi được khách lựa chọn nhiều.</p></div><Link to={catalog}>Khám phá thêm <ArrowRightOutlined /></Link></div><div className="home-product-grid">{popular.map(b => <ProductCard key={b.id} batch={b} store={data.stores.find(s => s.id === b.storeId)} compact />)}</div></section>
      <section className="home-steps" id="how-it-works"><div className="home-section-heading"><div><span className="home-eyebrow">ĐƠN GIẢN TRONG BA BƯỚC</span><h2>Cách FoodRescue hoạt động</h2></div></div><div className="home-step-grid"><div><ShoppingOutlined /><b>01. Chọn túi</b><p>Xem giá, số lượng và khung giờ nhận trước khi đặt.</p></div><div><GiftOutlined /><b>02. Đặt và thanh toán</b><p>Giữ túi bạn thích, nhận mã lấy hàng khi thanh toán.</p></div><div><EnvironmentOutlined /><b>03. Đến nhận</b><p>Đưa mã cho cửa hàng trong thời gian đã chọn.</p></div></div></section>
      <section className="home-partner"><div><span className="home-eyebrow">DÀNH CHO CỬA HÀNG</span><h2>Bán tốt hơn phần thực phẩm cuối ngày</h2><p>Đăng túi, quản lý đơn và theo dõi doanh thu trong một nơi.</p></div><Link to={user ? '/store/register' : '/register'}><Button size="large">Trở thành đối tác <ArrowRightOutlined /></Button></Link></section>
    </div>
    {publicPage && <footer className="home-public-footer"><div className="home-wrap home-public-footer-inner">
      <div><Link to="/" className="home-footer-brand" aria-label="FoodRescue"><img src="/foodrescue-logo.png" alt="FoodRescue" /></Link>
        <p>Kết nối thực phẩm còn tốt với người cần, cùng giảm lãng phí mỗi ngày.</p></div>
      <nav aria-label="Liên kết cuối trang"><Link to="/batches">Túi giải cứu</Link><Link to="/login">Đăng nhập</Link><Link to="/register">Đăng ký</Link></nav>
      <span>© 2026 FoodRescue</span>
    </div></footer>}
  </div>;
}
