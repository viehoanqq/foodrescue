import { useState } from 'react';
import { Alert, Button, Card, Empty, Form, Input, InputNumber, Modal, Radio, Segmented, Space, Steps, Tag, message } from 'antd';
import { ArrowLeftOutlined, BankOutlined, CheckCircleOutlined, ClockCircleOutlined, DeleteOutlined, EnvironmentOutlined, ShoppingCartOutlined } from '@ant-design/icons';
import { QRCodeSVG } from 'qrcode.react';
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom';
import { useAuth } from '../../auth/AuthProvider';
import { money, updateDemo } from '../../demo/data';
import { useDemo } from '../../demo/useDemo';
import './customerOrderFlow.css';

const statuses = [
  { value: 'ALL', label: 'Tất cả' },
  { value: 'PENDING_PAYMENT', label: 'Chờ thanh toán' },
  { value: 'CONFIRMED', label: 'Chờ nhận' },
  { value: 'COMPLETED', label: 'Đã hoàn thành' },
  { value: 'CANCELLED', label: 'Đã hủy' },
];
const statusLabels = { PENDING_PAYMENT: 'Chờ thanh toán', CONFIRMED: 'Chờ nhận', COMPLETED: 'Đã hoàn thành', CANCELLED: 'Đã hủy' };
const paymentLabels = { UNPAID: 'Chưa thanh toán', PAID: 'Đã thanh toán', REFUND_PENDING: 'Chờ hoàn tiền', REFUNDED: 'Đã hoàn tiền' };

function OrderStatus({ order }) {
  const color = order.status === 'COMPLETED' ? 'green' : order.status === 'CANCELLED' ? 'red' : 'gold';
  return <Tag color={color}>{statusLabels[order.status] || order.status}</Tag>;
}

function cartRows(data) {
  return data.cart.map(item => ({ ...item, batch: data.batches.find(batch => batch.id === item.batchId) }))
    .filter(item => item.batch);
}

function CartProduct({ row, data, editable = false }) {
  const category = data.categories.find(item => item.id === row.batch.categoryId && item.storeId === row.batch.storeId);
  const store = data.stores.find(item => item.id === row.batch.storeId);
  return <article className="checkout-product">
    <img src={category?.imageUrl || '/category-meal.svg'} alt={row.batch.title} />
    <div className="checkout-product-body"><Link to={`/batches/${row.batch.id}`}><strong>{row.batch.title}</strong></Link>
      <span>{store?.name || `Cửa hàng #${row.batch.storeId}`}</span>
      <span><ClockCircleOutlined /> Nhận {row.batch.pickup}</span>
      <div className="checkout-product-bottom"><b>{money(row.batch.rescuePrice)}</b>
        {editable ? <><InputNumber min={1} max={Math.min(5, row.batch.remaining)} value={row.quantity}
          onChange={value => updateDemo(next => {
            const current = next.cart.find(item => item.batchId === row.batchId);
            if (current) current.quantity = value || 1;
          })} /><Button danger type="text" icon={<DeleteOutlined />} aria-label={`Xóa ${row.batch.title}`}
          onClick={() => updateDemo(next => { next.cart = next.cart.filter(item => item.batchId !== row.batchId); })} /></>
          : <span>× {row.quantity}</span>}</div>
    </div>
  </article>;
}

function FlowHeader({ current }) {
  return <Steps className="checkout-steps" size="small" current={current} items={[
    { title: 'Giỏ hàng' }, { title: 'Thông tin nhận' }, { title: 'Thanh toán' }, { title: 'Hoàn tất' },
  ]} />;
}

export function CustomerCartPage() {
  const data = useDemo();
  const navigate = useNavigate();
  const rows = cartRows(data);
  const total = rows.reduce((sum, row) => sum + row.batch.rescuePrice * row.quantity, 0);
  const storeIds = [...new Set(rows.map(row => row.batch.storeId))];
  const invalid = rows.some(row => row.quantity > row.batch.remaining || row.batch.status !== 'AVAILABLE');
  const canContinue = rows.length > 0 && storeIds.length === 1 && !invalid;

  return <main className="checkout-page"><h1>Giỏ hàng</h1><FlowHeader current={0} />
    <div className="checkout-layout"><aside className="checkout-side-card"><h2>Thông tin đơn hàng</h2>
      <p>{rows.length} loại túi từ {storeIds.length} cửa hàng</p>
      <div className="checkout-total"><span>Tạm tính</span><strong>{money(total)}</strong></div>
      <p className="checkout-help">Mỗi đơn chỉ nhận túi từ một cửa hàng. Bạn sẽ nhập thông tin liên hệ và chọn thanh toán ở bước tiếp theo.</p>
      {storeIds.length > 1 && <Alert type="warning" showIcon message="Vui lòng chỉ giữ túi của một cửa hàng trong giỏ" />}
      {invalid && <Alert type="error" showIcon message="Có túi không còn đủ số lượng hoặc đã ngừng bán" />}
      <Button type="primary" size="large" block disabled={!canContinue} onClick={() => navigate('/customer/checkout')}>Tiếp tục đặt đơn</Button>
      <Link className="checkout-continue" to="/batches"><ArrowLeftOutlined /> Tiếp tục chọn túi</Link>
    </aside><section className="checkout-main-card"><div className="checkout-card-heading"><h2>Sản phẩm trong giỏ</h2><span>{rows.reduce((sum, row) => sum + row.quantity, 0)} túi</span></div>
      {rows.length ? rows.map(row => <CartProduct key={row.batchId} row={row} data={data} editable />) : <Empty description="Giỏ hàng đang trống" image={Empty.PRESENTED_IMAGE_SIMPLE} />}
    </section></div>
  </main>;
}

export function CheckoutInfoPage() {
  const data = useDemo();
  const { user } = useAuth();
  const navigate = useNavigate();
  const rows = cartRows(data);
  const storeIds = [...new Set(rows.map(row => row.batch.storeId))];
  if (!rows.length || storeIds.length !== 1) return <Navigate to="/customer/cart" replace />;
  const total = rows.reduce((sum, row) => sum + row.batch.rescuePrice * row.quantity, 0);
  const store = data.stores.find(item => item.id === storeIds[0]);

  function submit(values) {
    updateDemo(next => { next.checkoutDraft = { ...values, storeId: storeIds[0] }; });
    navigate('/customer/checkout/payment');
  }

  return <main className="checkout-page"><h1>Thông tin nhận hàng</h1><FlowHeader current={1} />
    <div className="checkout-layout"><section className="checkout-main-card"><h2>Người đến nhận</h2>
      <p>Đơn sẽ được chuẩn bị tại cửa hàng; địa chỉ và số điện thoại giúp cửa hàng liên hệ với bạn.</p>
      <Form layout="vertical" initialValues={{ fullName: data.checkoutDraft?.fullName || user?.fullName,
        phone: data.checkoutDraft?.phone || user?.phone, address: data.checkoutDraft?.address || '', note: data.checkoutDraft?.note || '' }} onFinish={submit}>
        <Form.Item name="fullName" label="Họ tên người nhận" rules={[{ required: true, whitespace: true, message: 'Nhập họ tên' }, { max: 100 }]}><Input maxLength={100} /></Form.Item>
        <Form.Item name="phone" label="Số điện thoại" rules={[{ required: true, pattern: /^0\d{9,10}$/, message: 'Nhập số điện thoại hợp lệ' }]}><Input maxLength={11} /></Form.Item>
        <Form.Item name="address" label="Địa chỉ liên hệ" rules={[{ required: true, whitespace: true, message: 'Nhập địa chỉ' }, { max: 255 }]}><Input maxLength={255} placeholder="Số nhà, đường, phường, quận, thành phố" /></Form.Item>
        <Form.Item name="note" label="Ghi chú cho cửa hàng" rules={[{ max: 255 }]}><Input.TextArea rows={3} maxLength={255} showCount /></Form.Item>
        <Space><Button onClick={() => navigate('/customer/cart')}>Quay lại giỏ</Button><Button type="primary" htmlType="submit">Tiếp tục thanh toán</Button></Space>
      </Form>
    </section><aside className="checkout-side-card"><h2>Đơn tại {store?.name}</h2><p><EnvironmentOutlined /> Nhận tại {store?.address}</p>
      {rows.map(row => <p key={row.batchId}>{row.batch.title} × {row.quantity}</p>)}
      <div className="checkout-total"><span>Tổng tiền</span><strong>{money(total)}</strong></div>
    </aside></div>
  </main>;
}

export function CheckoutPaymentPage() {
  const data = useDemo();
  const navigate = useNavigate();
  const [method, setMethod] = useState('BANK_TRANSFER');
  const draft = data.checkoutDraft;
  const rows = cartRows(data);
  const total = rows.reduce((sum, row) => sum + row.batch.rescuePrice * row.quantity, 0);
  if (!draft || !rows.length || rows.some(row => row.batch.storeId !== draft.storeId)) return <Navigate to="/customer/cart" replace />;

  function createOrder() {
    const latest = rows.map(row => ({ batchId: row.batchId, quantity: row.quantity }));
    const live = data.batches;
    if (latest.some(item => {
      const batch = live.find(row => row.id === item.batchId);
      return !batch || batch.status !== 'AVAILABLE' || batch.remaining < item.quantity;
    })) { message.error('Số lượng túi đã thay đổi. Vui lòng kiểm tra lại giỏ hàng.'); navigate('/customer/cart'); return; }
    const orderId = Math.max(Date.now(), ...data.orders.map(order => order.id + 1));
    updateDemo(next => {
      next.orders.unshift({ id: orderId, customer: draft.fullName, storeId: draft.storeId,
        items: rows.map(row => ({ batchId: row.batchId, quantity: row.quantity, price: row.batch.rescuePrice })),
        total, status: 'PENDING_PAYMENT', paymentStatus: 'UNPAID', paymentMethod: method,
        pickupCode: `FR${String(orderId).slice(-6)}`, createdAt: new Date().toLocaleString('vi-VN'),
        contact: { fullName: draft.fullName, phone: draft.phone, address: draft.address, note: draft.note || '' } });
      rows.forEach(row => { const batch = next.batches.find(item => item.id === row.batchId); batch.remaining -= row.quantity; });
      next.cart = [];
      next.checkoutDraft = null;
    });
    navigate(`/customer/payment/${orderId}`);
  }

  return <main className="checkout-page"><h1>Chọn phương thức thanh toán</h1><FlowHeader current={2} />
    <div className="checkout-layout"><section className="checkout-main-card"><h2>Thanh toán</h2>
      <Radio.Group value={method} onChange={event => setMethod(event.target.value)} className="checkout-payment-methods">
        <Radio value="BANK_TRANSFER"><BankOutlined /> Chuyển khoản ngân hàng (mô phỏng)</Radio>
        <Radio value="VNPAY_DEMO"><ShoppingCartOutlined /> VNPay (mô phỏng)</Radio>
      </Radio.Group>
      <Alert type="info" showIcon message="Đây là dữ liệu mẫu, không chuyển tiền thật." style={{ margin: '20px 0' }} />
      <Space><Button onClick={() => navigate('/customer/checkout')}>Sửa thông tin</Button><Button type="primary" onClick={createOrder}>Tạo đơn và tiếp tục</Button></Space>
    </section><aside className="checkout-side-card"><h2>Xác nhận đơn</h2>
      <p>Người nhận: {draft.fullName}</p><p>Điện thoại: {draft.phone}</p><p>Địa chỉ liên hệ: {draft.address}</p>
      {rows.map(row => <p key={row.batchId}>{row.batch.title} × {row.quantity}</p>)}
      <div className="checkout-total"><span>Tổng thanh toán</span><strong>{money(total)}</strong></div>
    </aside></div>
  </main>;
}

export function CustomerPaymentPage() {
  const { orderId } = useParams();
  const navigate = useNavigate();
  const data = useDemo();
  const order = data.orders.find(item => item.id === Number(orderId));
  if (!order) return <Navigate to="/me/orders" replace />;
  if (order.paymentStatus === 'PAID') return <Navigate to={`/customer/payment/${order.id}/complete`} replace />;
  if (order.status !== 'PENDING_PAYMENT') return <Navigate to={`/me/orders/${order.id}`} replace />;

  function confirmPayment() {
    updateDemo(next => {
      const current = next.orders.find(item => item.id === order.id);
      if (current.status !== 'PENDING_PAYMENT') return;
      current.paymentStatus = 'PAID';
      current.status = 'CONFIRMED';
    });
    navigate(`/customer/payment/${order.id}/complete`);
  }

  return <main className="checkout-page checkout-payment-page"><h1>{order.paymentMethod === 'VNPAY_DEMO' ? 'Thanh toán VNPay' : 'Thông tin chuyển khoản'}</h1><FlowHeader current={2} />
    <Card className="checkout-transfer-card"><Alert type="warning" showIcon message="MÔ PHỎNG — không chuyển tiền thật" description="Bấm nút xác nhận để thử luồng thanh toán và nhận mã lấy hàng." />
      <div className="checkout-transfer-body"><div className="checkout-qr"><QRCodeSVG value={`FOODRESCUE-DEMO|${order.id}|${order.total}`} size={180} /><span>Mã QR mô phỏng</span></div>
        <div><h2>Đơn #{order.id}</h2>{order.paymentMethod === 'BANK_TRANSFER'
          ? <><p>Ngân hàng: <strong>Ngân hàng mẫu</strong></p><p>Số tài khoản: <strong>0000000000</strong></p><p>Nội dung: <strong>FR {order.id}</strong></p></>
          : <p>Quét mã VNPay mô phỏng để xem thông tin thanh toán.</p>}
          <p>Số tiền: <strong>{money(order.total)}</strong></p>
          <Button type="primary" size="large" onClick={confirmPayment}>Xác nhận thanh toán mô phỏng</Button>
          <p><Link to={`/me/orders/${order.id}`}>Xem đơn trước khi thanh toán</Link></p></div></div>
    </Card>
  </main>;
}

export function CustomerPaymentCompletePage() {
  const { orderId } = useParams();
  const data = useDemo();
  const order = data.orders.find(item => item.id === Number(orderId));
  if (!order) return <Navigate to="/me/orders" replace />;
  if (order.paymentStatus !== 'PAID') return <Navigate to={`/customer/payment/${order.id}`} replace />;

  return <main className="checkout-page checkout-finish-page"><FlowHeader current={3} />
    <Card><CheckCircleOutlined className="checkout-success-icon" /><h1>Đặt túi thành công</h1>
      <p>Đơn #{order.id} đã thanh toán. Đến cửa hàng trong khung giờ nhận và cung cấp mã bên dưới.</p>
      <div className="checkout-pickup-code">{order.pickupCode}</div>
      <strong>{money(order.total)}</strong><div className="checkout-finish-actions"><Link to={`/me/orders/${order.id}`}><Button type="primary">Xem chi tiết đơn</Button></Link><Link to="/batches"><Button>Tiếp tục chọn túi</Button></Link></div>
    </Card>
  </main>;
}

export function CustomerOrdersPage() {
  const data = useDemo();
  const [status, setStatus] = useState('ALL');
  const rows = data.orders.filter(order => status === 'ALL' || order.status === status);
  return <div className="customer-orders-page"><div className="customer-orders-heading"><h1>Đơn của tôi</h1><p>Theo dõi thanh toán và thời gian nhận túi.</p></div>
    <div className="customer-order-filters"><Segmented block options={statuses.map(item => ({ value: item.value, label: `${item.label} (${item.value === 'ALL' ? data.orders.length : data.orders.filter(order => order.status === item.value).length})` }))} value={status} onChange={setStatus} /></div>
    {rows.length ? rows.map(order => <Link className="customer-order-card" to={`/me/orders/${order.id}`} key={order.id}>
      <div><strong>Đơn #{order.id}</strong><span>{order.createdAt}</span></div>
      <p>{data.stores.find(store => store.id === order.storeId)?.name || `Cửa hàng #${order.storeId}`}</p>
      <div><OrderStatus order={order} /><span>{paymentLabels[order.paymentStatus] || order.paymentStatus}</span><b>{money(order.total)}</b></div>
    </Link>) : <Empty description="Chưa có đơn ở trạng thái này" />}
  </div>;
}

export function CustomerOrderDetailPage() {
  const { orderId } = useParams();
  const data = useDemo();
  const navigate = useNavigate();
  const order = data.orders.find(item => item.id === Number(orderId));
  if (!order) return <Empty description="Không tìm thấy đơn hàng" />;
  const store = data.stores.find(item => item.id === order.storeId);

  function cancel() {
    Modal.confirm({ title: 'Hủy đơn hàng?', content: 'Đơn đã thanh toán sẽ chuyển sang chờ hoàn tiền.',
      okText: 'Hủy đơn', okButtonProps: { danger: true }, cancelText: 'Giữ đơn',
      onOk: () => {
        updateDemo(next => {
          const current = next.orders.find(item => item.id === order.id);
          if (!['PENDING_PAYMENT', 'CONFIRMED'].includes(current.status)) return;
          current.status = 'CANCELLED';
          current.items.forEach(item => { const batch = next.batches.find(row => row.id === item.batchId); if (batch) batch.remaining += item.quantity; });
          if (current.paymentStatus === 'PAID') {
            current.paymentStatus = 'REFUND_PENDING';
            next.refunds.push({ id: Date.now(), orderId: current.id, amount: current.total, status: 'PENDING', reason: 'CUSTOMER_CANCELLED' });
          }
        });
        message.success('Đã hủy đơn');
      },
    });
  }

  return <div className="customer-orders-page"><Link to="/me/orders"><ArrowLeftOutlined /> Tất cả đơn</Link>
    <div className="customer-orders-heading"><h1>Đơn #{order.id}</h1><OrderStatus order={order} /></div>
    <Card className="customer-order-detail-card"><h2>{store?.name || `Cửa hàng #${order.storeId}`}</h2>
      <p><EnvironmentOutlined /> {store?.address}</p><p>Ngày tạo: {order.createdAt}</p>
      <p>Thanh toán: <Tag color={order.paymentStatus === 'PAID' ? 'green' : 'gold'}>{paymentLabels[order.paymentStatus] || order.paymentStatus}</Tag></p>
      {order.contact && <div className="customer-order-contact"><h3>Thông tin nhận hàng</h3><p>{order.contact.fullName} · {order.contact.phone}</p><p>{order.contact.address}</p>{order.contact.note && <p>Ghi chú: {order.contact.note}</p>}</div>}
      <h3>Túi đã đặt</h3>{order.items.map(item => <p className="customer-order-item" key={item.batchId}>
        <span>{data.batches.find(batch => batch.id === item.batchId)?.title || `Túi #${item.batchId}`} × {item.quantity}</span><b>{money(item.price * item.quantity)}</b></p>)}
      <div className="checkout-total"><span>Tổng tiền</span><strong>{money(order.total)}</strong></div>
      {order.paymentStatus === 'PAID' && <div className="checkout-pickup-code">Mã nhận hàng: {order.pickupCode}</div>}
      <Space style={{ marginTop: 20 }} wrap>{order.status === 'PENDING_PAYMENT' && <Button type="primary" onClick={() => navigate(`/customer/payment/${order.id}`)}>Tiếp tục thanh toán</Button>}
        {['PENDING_PAYMENT', 'CONFIRMED'].includes(order.status) && <Button danger onClick={cancel}>Hủy đơn</Button>}</Space>
    </Card>
  </div>;
}
