import { Alert, Button, Card, Col, Descriptions, Modal, Row, Statistic, Table, Tag, message } from 'antd';
import { money, updateDemo } from './data';
import { useDemo } from './useDemo';

const titles = { dashboard: 'Tổng quan', orders: 'Đơn hàng', refunds: 'Hoàn tiền', payouts: 'Chi trả', finance: 'Đối soát' };
const statuses = { PENDING_PAYMENT: 'Chờ thanh toán', CONFIRMED: 'Chờ nhận', COMPLETED: 'Đã hoàn thành', CANCELLED: 'Đã hủy', PENDING: 'Chờ xử lý', PAID: 'Đã chi trả' };

function status(value) {
  return <Tag color={['COMPLETED', 'PAID'].includes(value) ? 'green' : value === 'CANCELLED' ? 'red' : 'gold'}>{statuses[value] || value}</Tag>;
}

function confirmAction({ title, content, danger = false, run }) {
  Modal.confirm({ title, content, okText: 'Xác nhận', cancelText: 'Quay lại',
    okButtonProps: { danger }, onOk: run });
}

export default function DemoAdminPage({ section }) {
  const data = useDemo();
  const paidOrders = data.orders.filter(order => order.paymentStatus === 'PAID');
  const gmv = paidOrders.reduce((sum, order) => sum + order.total, 0);
  const refundPending = data.refunds.filter(row => row.status === 'PENDING');
  const payoutPending = data.payouts.filter(row => row.status === 'PENDING');

  function cancelOrder(order) {
    confirmAction({ title: `Hủy đơn #${order.id}?`, danger: true,
      content: order.paymentStatus === 'PAID' ? 'Đơn đã thanh toán sẽ chuyển sang chờ hoàn tiền.' : 'Số túi của đơn sẽ được trả lại kho mẫu.',
      run: () => { updateDemo(next => {
        const current = next.orders.find(row => row.id === order.id);
        if (!current || !['PENDING_PAYMENT', 'CONFIRMED'].includes(current.status)) return;
        current.status = 'CANCELLED';
        current.items.forEach(item => { const batch = next.batches.find(row => row.id === item.batchId); if (batch) batch.remaining += item.quantity; });
        if (current.paymentStatus === 'PAID') {
          current.paymentStatus = 'REFUND_PENDING';
          if (!next.refunds.some(row => row.orderId === current.id)) next.refunds.push({ id: Date.now(), orderId: current.id, amount: current.total, status: 'PENDING', reason: 'ADMIN_CANCELLED' });
        }
      }); message.success('Đã hủy đơn'); } });
  }

  function completeRefund(refund) {
    confirmAction({ title: `Xác nhận đã hoàn ${money(refund.amount)}?`,
      content: `Đơn #${refund.orderId} sẽ được ghi nhận đã hoàn tiền.`,
      run: () => { updateDemo(next => {
        const current = next.refunds.find(row => row.id === refund.id);
        if (!current || current.status !== 'PENDING') return;
        current.status = 'COMPLETED';
        const order = next.orders.find(row => row.id === current.orderId);
        if (order) order.paymentStatus = 'REFUNDED';
      }); message.success('Đã xác nhận hoàn tiền'); } });
  }

  function completePayout(payout) {
    confirmAction({ title: `Xác nhận đã chi trả ${money(payout.amount)}?`,
      content: `Kỳ ${payout.period}. Chỉ xác nhận khi cửa hàng đã nhận tiền.`,
      run: () => { updateDemo(next => {
        const current = next.payouts.find(row => row.id === payout.id);
        if (current && current.status !== 'PAID') current.status = 'PAID';
      }); message.success('Đã xác nhận chi trả'); } });
  }

  const columns = section === 'orders' ? [
    { title: 'Mã đơn', dataIndex: 'id', render: id => `#${id}` },
    { title: 'Cửa hàng', render: (_, row) => data.stores.find(store => store.id === row.storeId)?.name },
    { title: 'Số tiền', render: (_, row) => money(row.total) },
    { title: 'Trạng thái', render: (_, row) => status(row.status) },
    { title: 'Thao tác', render: (_, row) => <Button danger disabled={!['CONFIRMED', 'PENDING_PAYMENT'].includes(row.status)} onClick={() => cancelOrder(row)}>Hủy đơn</Button> },
  ] : section === 'refunds' ? [
    { title: 'Mã hoàn', dataIndex: 'id' }, { title: 'Đơn', dataIndex: 'orderId', render: id => `#${id}` },
    { title: 'Số tiền', render: (_, row) => money(row.amount) },
    { title: 'Trạng thái', render: (_, row) => status(row.status) },
    { title: 'Thao tác', render: (_, row) => <Button disabled={row.status === 'COMPLETED'} onClick={() => completeRefund(row)}>Xác nhận hoàn</Button> },
  ] : [
    { title: 'Kỳ', dataIndex: 'period' },
    { title: 'Cửa hàng', render: (_, row) => data.stores.find(store => store.id === row.storeId)?.name },
    { title: 'Thực nhận', render: (_, row) => money(row.amount) },
    { title: 'Trạng thái', render: (_, row) => status(row.status) },
    { title: 'Thao tác', render: (_, row) => <Button disabled={row.status === 'PAID'} onClick={() => completePayout(row)}>Đã chi trả</Button> },
  ];

  return <main style={{ maxWidth: 1180, padding: '24px 0', margin: '0 auto' }}><h1>{titles[section]}</h1>
    {section === 'dashboard' ? <><Row gutter={[16, 16]}>{[
      ['Cửa hàng', data.stores.length], ['Đơn hàng', data.orders.length],
      ['Doanh thu', money(gmv)], ['Chờ hoàn', refundPending.length],
    ].map(([title, value]) => <Col xs={12} md={6} key={title}><Card><Statistic title={title} value={value} /></Card></Col>)}</Row>
      <Alert style={{ marginTop: 16 }} type="info" message="Số liệu từ dữ liệu mẫu trong trình duyệt." /></>
      : section === 'finance' ? <Card><Descriptions column={1} bordered items={[
        { key: 'gmv', label: 'GMV đã thanh toán', children: money(gmv) },
        { key: 'refunds', label: 'Hoàn tiền chờ xử lý', children: money(refundPending.reduce((sum, row) => sum + row.amount, 0)) },
        { key: 'payouts', label: 'Chi trả chờ xác nhận', children: money(payoutPending.reduce((sum, row) => sum + row.amount, 0)) },
      ]} /></Card>
        : <Table rowKey="id" dataSource={data[section] || []} columns={columns} />}
  </main>;
}
