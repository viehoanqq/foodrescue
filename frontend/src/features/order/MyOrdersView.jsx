import { useEffect, useState } from 'react';
import { Card, Table, Tag, Button, Space, Modal, Typography, Radio, message, Input, Alert } from 'antd';
import { QrcodeOutlined, CreditCardOutlined, CloseCircleOutlined } from '@ant-design/icons';
import { QRCodeSVG } from 'qrcode.react';
import { orderApi, paymentApi } from './api';
import { formatMoney, formatDateTime, formatPickupWindow } from '../../shared/utils/format';
import { ORDER_STATUS, PAYMENT_STATUS } from '../../shared/constants/status';

const { Title, Text } = Typography;

export default function MyOrdersView({ onSelectOrder }) {
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(false);
  const [filterStatus, setFilterStatus] = useState(null);
  const [selectedOrder, setSelectedOrder] = useState(null);
  const [pickupModalOpen, setPickupModalOpen] = useState(false);
  const [cancelModalOrder, setCancelModalOrder] = useState(null);
  const [cancelReason, setCancelReason] = useState('');
  const [cancelling, setCancelling] = useState(false);

  const loadOrders = async (status) => {
    setLoading(true);
    try {
      const data = await orderApi.getMyOrders(status);
      setOrders(data);
    } catch (err) {
      message.error(err.message || 'Không thể tải danh sách đơn hàng');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadOrders(filterStatus);
  }, [filterStatus]);

  const handleOpenPickup = (order) => {
    setSelectedOrder(order);
    setPickupModalOpen(true);
  };

  const handlePayVNPay = async (orderId) => {
    try {
      const res = await paymentApi.createPaymentUrl(orderId);
      if (res.paymentUrl) {
        window.open(res.paymentUrl, '_blank');
      }
    } catch (err) {
      message.error(err.message || 'Không thể tạo liên kết thanh toán');
    }
  };

  const handleMockPay = async (orderId) => {
    try {
      const res = await paymentApi.mockSuccess(orderId);
      message.success(res.message || 'Thanh toán thành công!');
      loadOrders(filterStatus);
    } catch (err) {
      message.error(err.message);
    }
  };

  const handleCancelOrder = async () => {
    if (!cancelModalOrder) return;
    setCancelling(true);
    try {
      await orderApi.cancelOrder(cancelModalOrder.id, cancelReason);
      message.success('Đã huỷ đơn hàng thành công');
      setCancelModalOrder(null);
      setCancelReason('');
      loadOrders(filterStatus);
    } catch (err) {
      message.error(err.message || 'Không thể huỷ đơn');
    } finally {
      setCancelling(false);
    }
  };

  const columns = [
    {
      title: 'Mã đơn',
      dataIndex: 'orderCode',
      key: 'orderCode',
      render: (code, record) => (
        <div>
          <Text strong>{code}</Text>
          <br />
          <Text type="secondary" style={{ fontSize: 12 }}>{formatDateTime(record.createdAt)}</Text>
        </div>
      ),
    },
    {
      title: 'Cửa hàng',
      dataIndex: 'storeName',
      key: 'storeName',
      render: (name, record) => (
        <div>
          <Text strong>{name}</Text>
          <br />
          <Text type="secondary" style={{ fontSize: 12 }}>{record.storeAddress}</Text>
        </div>
      ),
    },
    {
      title: 'Khung giờ nhận',
      key: 'pickupTime',
      render: (_, record) => (
        <Tag color="cyan">
          {formatPickupWindow(record.pickupStart, record.pickupEnd)}
        </Tag>
      ),
    },
    {
      title: 'Tổng tiền',
      dataIndex: 'totalAmount',
      key: 'totalAmount',
      render: (amt, record) => (
        <div>
          <Text strong style={{ color: '#fa8c16' }}>{formatMoney(amt)}</Text>
          <br />
          <Text type="secondary" style={{ fontSize: 12 }}>{record.itemCount} túi</Text>
        </div>
      ),
    },
    {
      title: 'Trạng thái',
      key: 'status',
      render: (_, record) => {
        const orderSt = ORDER_STATUS[record.status] || { label: record.status, color: 'default' };
        const paySt = PAYMENT_STATUS[record.paymentStatus] || { label: record.paymentStatus, color: 'default' };
        return (
          <Space direction="vertical" size={2}>
            <Tag color={orderSt.color}>{orderSt.label}</Tag>
            <Tag color={paySt.color} style={{ fontSize: 11 }}>{paySt.label}</Tag>
          </Space>
        );
      },
    },
    {
      title: 'Thao tác',
      key: 'actions',
      render: (_, record) => (
        <Space direction="vertical" size={4}>
          <Space size={4}>
            <Button
              size="small"
              type="primary"
              icon={<QrcodeOutlined />}
              onClick={() => handleOpenPickup(record)}
              style={{ background: '#52c41a', borderColor: '#52c41a' }}
            >
              Mã nhận hàng
            </Button>
            <Button
              size="small"
              onClick={() => onSelectOrder && onSelectOrder(record.id)}
            >
              Chi tiết hóa đơn
            </Button>
          </Space>

          {record.status === 'PENDING_PAYMENT' && (
            <Space size={4}>
              <Button
                size="small"
                type="primary"
                icon={<CreditCardOutlined />}
                onClick={() => handlePayVNPay(record.id)}
                style={{ background: '#005baa', borderColor: '#005baa' }}
              >
                VNPay
              </Button>
              <Button
                size="small"
                onClick={() => handleMockPay(record.id)}
              >
                Sandbox Test
              </Button>
              {record.canCancel && (
                <Button
                  size="small"
                  danger
                  icon={<CloseCircleOutlined />}
                  onClick={() => setCancelModalOrder(record)}
                >
                  Huỷ
                </Button>
              )}
            </Space>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div style={{ maxWidth: 1000, margin: '0 auto', padding: '16px 0' }}>
      <Card title="Đơn hàng của tôi">
        <div style={{ marginBottom: 16 }}>
          <Radio.Group
            value={filterStatus}
            onChange={(e) => setFilterStatus(e.target.value)}
            buttonStyle="solid"
          >
            <Radio.Button value={null}>Tất cả</Radio.Button>
            <Radio.Button value="PENDING_PAYMENT">Chờ thanh toán</Radio.Button>
            <Radio.Button value="CONFIRMED">Chờ nhận hàng</Radio.Button>
            <Radio.Button value="COMPLETED">Đã nhận hàng</Radio.Button>
            <Radio.Button value="CANCELLED">Đã huỷ</Radio.Button>
          </Radio.Group>
        </div>

        <Table
          dataSource={orders}
          columns={columns}
          rowKey="id"
          loading={loading}
          pagination={{ pageSize: 8 }}
        />
      </Card>

      {/* Modal hiển thị mã nhận hàng và QR Code */}
      <Modal
        title="Mã nhận hàng tại quầy"
        open={pickupModalOpen}
        onCancel={() => setPickupModalOpen(false)}
        footer={null}
        destroyOnClose
      >
        {selectedOrder && (
          <div style={{ textAlign: 'center', padding: '16px 0' }}>
            <Text type="secondary">Cửa hàng: </Text>
            <Title level={4} style={{ margin: '4px 0' }}>{selectedOrder.storeName}</Title>
            <Text type="secondary">{selectedOrder.storeAddress}</Text>

            <div style={{ margin: '20px 0', padding: '16px', background: '#f6ffed', border: '2px dashed #b7eb8f', borderRadius: 8 }}>
              <Text type="secondary">Đọc mã này cho nhân viên quầy:</Text>
              <Title level={1} style={{ margin: '8px 0', letterSpacing: 6, color: '#389e0d' }}>
                {selectedOrder.pickupCode}
              </Title>
              <div style={{ display: 'flex', justifyContent: 'center', marginTop: 12 }}>
                <QRCodeSVG value={selectedOrder.pickupCode} size={160} />
              </div>
            </div>

            <div style={{ textAlign: 'left', background: '#fafafa', padding: 12, borderRadius: 6, marginBottom: 12 }}>
              <div><Text type="secondary">Khung giờ nhận: </Text><Tag color="cyan">{formatPickupWindow(selectedOrder.pickupStart, selectedOrder.pickupEnd)}</Tag></div>
              <div><Text type="secondary">Tổng tiền: </Text><Text strong>{formatMoney(selectedOrder.totalAmount)}</Text></div>
              <div style={{ marginTop: 8 }}>
                <Text type="secondary">Các túi đã đặt: </Text>
                <ul style={{ margin: '4px 0', paddingLeft: 20 }}>
                  {selectedOrder.items?.map((it) => (
                    <li key={it.id}>{it.title} x {it.quantity}</li>
                  ))}
                </ul>
              </div>
            </div>

            {selectedOrder.status === 'CONFIRMED' ? (
              <Alert type="success" message="Đơn đã thanh toán, sẵn sàng nhận hàng tại quầy!" />
            ) : selectedOrder.status === 'PENDING_PAYMENT' ? (
              <Alert type="warning" message="Đơn chưa thanh toán! Vui lòng thanh toán trước khi lấy hàng." />
            ) : (
              <Alert type="info" message={`Trạng thái: ${selectedOrder.status}`} />
            )}
          </div>
        )}
      </Modal>

      {/* Modal huỷ đơn hàng */}
      <Modal
        title="Xác nhận huỷ đơn hàng"
        open={!!cancelModalOrder}
        onCancel={() => setCancelModalOrder(null)}
        onOk={handleCancelOrder}
        confirmLoading={cancelling}
        okText="Xác nhận huỷ"
        okButtonProps={{ danger: true }}
      >
        <p>Bạn có chắc muốn huỷ đơn <Text strong>{cancelModalOrder?.orderCode}</Text>?</p>
        <p>Các túi cứu trợ trong đơn sẽ được trả lại kho cho cửa hàng bán tiếp.</p>
        <Input.TextArea
          placeholder="Nhập lý do huỷ đơn (không bắt buộc)"
          value={cancelReason}
          onChange={(e) => setCancelReason(e.target.value)}
          rows={3}
        />
      </Modal>
    </div>
  );
}
