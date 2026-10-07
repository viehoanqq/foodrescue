import { useEffect, useState } from 'react';
import { Card, Table, Button, Space, Typography, Tag, Alert, message, Modal, Empty, Spin } from 'antd';
import { DeleteOutlined, ShoppingCartOutlined, CreditCardOutlined, PlusOutlined, MinusOutlined } from '@ant-design/icons';
import { cartApi, orderApi, paymentApi } from './api';
import { formatMoney, formatPickupWindow } from '../../shared/utils/format';

const { Title, Text } = Typography;

export default function CartView({ onOrderCreated }) {
  const [cart, setCart] = useState(null);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [paymentModalOrder, setPaymentModalOrder] = useState(null);

  const loadCart = async () => {
    setLoading(true);
    try {
      const data = await cartApi.getCart();
      setCart(data);
    } catch (err) {
      message.error(err.message || 'Không thể tải giỏ hàng');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadCart();
  }, []);

  const handleUpdateQty = async (batchId, newQty) => {
    if (newQty < 1 || newQty > 5) return;
    try {
      const updated = await cartApi.updateQuantity(batchId, newQty);
      setCart(updated);
      message.success('Đã cập nhật số lượng');
    } catch (err) {
      message.error(err.message);
    }
  };

  const handleRemoveItem = async (batchId) => {
    try {
      await cartApi.removeItem(batchId);
      message.success('Đã xoá túi khỏi giỏ');
      loadCart();
    } catch (err) {
      message.error(err.message);
    }
  };

  const handleClearCart = async () => {
    try {
      await cartApi.clearCart();
      message.success('Đã làm trống giỏ hàng');
      loadCart();
    } catch (err) {
      message.error(err.message);
    }
  };

  const handleCreateOrder = async () => {
    if (!cart || !cart.items || cart.items.length === 0) return;
    setSubmitting(true);
    try {
      const newOrder = await orderApi.createOrder();
      message.success(`Tạo đơn ${newOrder.orderCode} thành công!`);
      loadCart();
      setPaymentModalOrder(newOrder);
      if (onOrderCreated) onOrderCreated(newOrder);
    } catch (err) {
      message.error(err.message || 'Không thể tạo đơn hàng');
    } finally {
      setSubmitting(false);
    }
  };

  const handleVNPayPayment = async (orderId) => {
    try {
      const res = await paymentApi.createPaymentUrl(orderId);
      if (res.paymentUrl) {
        window.open(res.paymentUrl, '_blank');
      }
    } catch (err) {
      message.error(err.message || 'Lỗi khởi tạo thanh toán VNPay');
    }
  };

  const handleMockPayment = async (orderId) => {
    try {
      const res = await paymentApi.mockSuccess(orderId);
      message.success(res.message || 'Thanh toán Sandbox thành công!');
      setPaymentModalOrder(null);
      if (onOrderCreated) onOrderCreated();
    } catch (err) {
      message.error(err.message);
    }
  };

  if (loading && !cart) {
    return <div style={{ textAlign: 'center', padding: 60 }}><Spin size="large" /></div>;
  }

  const columns = [
    {
      title: 'Túi cứu trợ',
      dataIndex: 'title',
      key: 'title',
      render: (title, record) => (
        <div>
          <Text strong>{title}</Text>
          <br />
          <Text type="secondary" style={{ fontSize: 12 }}>
            Giờ lấy: {formatPickupWindow(record.pickupStart, record.pickupEnd)}
          </Text>
          {record.outOfStock && (
            <div><Tag color="error">Hết hàng hoặc quá giờ</Tag></div>
          )}
        </div>
      ),
    },
    {
      title: 'Đơn giá',
      dataIndex: 'rescuePrice',
      key: 'rescuePrice',
      render: (price) => formatMoney(price),
    },
    {
      title: 'Số lượng',
      dataIndex: 'quantity',
      key: 'quantity',
      render: (qty, record) => (
        <Space>
          <Button
            size="small"
            icon={<MinusOutlined />}
            disabled={qty <= 1}
            onClick={() => handleUpdateQty(record.batchId, qty - 1)}
          />
          <Text strong>{qty}</Text>
          <Button
            size="small"
            icon={<PlusOutlined />}
            disabled={qty >= 5 || qty >= record.remainingQuantity}
            onClick={() => handleUpdateQty(record.batchId, qty + 1)}
          />
        </Space>
      ),
    },
    {
      title: 'Thành tiền',
      dataIndex: 'subtotal',
      key: 'subtotal',
      render: (subtotal) => <Text strong style={{ color: '#fa8c16' }}>{formatMoney(subtotal)}</Text>,
    },
    {
      title: '',
      key: 'action',
      render: (_, record) => (
        <Button
          type="text"
          danger
          icon={<DeleteOutlined />}
          onClick={() => handleRemoveItem(record.batchId)}
        />
      ),
    },
  ];

  return (
    <div style={{ maxWidth: 900, margin: '0 auto', padding: '16px 0' }}>
      <Card
        title={
          <Space>
            <ShoppingCartOutlined style={{ fontSize: 22, color: '#fa8c16' }} />
            <span>Giỏ hàng giải cứu thực phẩm</span>
          </Space>
        }
        extra={
          cart?.items?.length > 0 && (
            <Button danger type="link" onClick={handleClearCart}>
              Xoá toàn bộ giỏ
            </Button>
          )
        }
      >
        {!cart || cart.items.length === 0 ? (
          <Empty description="Giỏ hàng của bạn đang trống" />
        ) : (
          <div>
            <div style={{ marginBottom: 16 }}>
              <Text type="secondary">Cửa hàng: </Text>
              <Text strong>{cart.storeName}</Text>
            </div>

            {cart.pickupConflict && (
              <Alert
                type="error"
                showIcon
                message="Xung đột khung giờ lấy hàng"
                description="Các túi trong giỏ hàng không có khung giờ lấy chung. Vui lòng bỏ bớt túi để giờ lấy hợp lệ."
                style={{ marginBottom: 16 }}
              />
            )}

            {cart.hasUnavailableItems && (
              <Alert
                type="warning"
                showIcon
                message="Có túi không đủ số lượng hoặc hết hạn"
                description="Một số túi trong giỏ đã hết hàng hoặc quá giờ. Vui lòng điều chỉnh trước khi đặt đơn."
                style={{ marginBottom: 16 }}
              />
            )}

            <Table
              dataSource={cart.items}
              columns={columns}
              rowKey="batchId"
              pagination={false}
              size="middle"
            />

            <div style={{ marginTop: 24, padding: 16, background: '#fafafa', borderRadius: 8, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <div><Text type="secondary">Tổng số túi: </Text><Text strong>{cart.totalItems} túi</Text></div>
                {cart.pickupStart && cart.pickupEnd && (
                  <div>
                    <Text type="secondary">Khung giờ nhận đơn: </Text>
                    <Tag color="cyan">{formatPickupWindow(cart.pickupStart, cart.pickupEnd)}</Tag>
                  </div>
                )}
              </div>
              <div style={{ textAlign: 'right' }}>
                <div><Text type="secondary">Tổng thanh toán:</Text></div>
                <Title level={3} style={{ color: '#fa8c16', margin: 0 }}>
                  {formatMoney(cart.totalAmount)}
                </Title>
              </div>
            </div>

            <div style={{ marginTop: 20, textAlign: 'right' }}>
              <Button
                type="primary"
                size="large"
                icon={<CreditCardOutlined />}
                loading={submitting}
                disabled={cart.pickupConflict || cart.hasUnavailableItems}
                onClick={handleCreateOrder}
                style={{ background: '#fa8c16', borderColor: '#fa8c16', minWidth: 220 }}
              >
                Tạo đơn nhiều túi & Thanh toán
              </Button>
            </div>
          </div>
        )}
      </Card>

      {/* Modal thanh toán VNPay sau khi tạo đơn */}
      <Modal
        title="Đơn hàng đã được tạo thành công!"
        open={!!paymentModalOrder}
        onCancel={() => setPaymentModalOrder(null)}
        footer={null}
        destroyOnClose
      >
        {paymentModalOrder && (
          <div style={{ textAlign: 'center', padding: '16px 0' }}>
            <p>Mã đơn hàng: <Text strong>{paymentModalOrder.orderCode}</Text></p>
            <p>Tổng tiền thanh toán: <Text strong style={{ color: '#fa8c16', fontSize: 20 }}>{formatMoney(paymentModalOrder.totalAmount)}</Text></p>
            <p>Mã nhận hàng tạm thời: <Tag color="blue" style={{ fontSize: 16, padding: '4px 10px' }}>{paymentModalOrder.pickupCode}</Tag></p>
            <Alert
              type="info"
              showIcon
              message="Vui lòng thanh toán trong vòng 10 phút"
              description="Sau 10 phút, hệ thống sẽ tự động huỷ đơn và hoàn trả túi về cửa hàng."
              style={{ margin: '16px 0', textAlign: 'left' }}
            />
            <Space direction="vertical" style={{ width: '100%' }}>
              <Button
                type="primary"
                block
                size="large"
                style={{ background: '#005baa', borderColor: '#005baa' }}
                onClick={() => handleVNPayPayment(paymentModalOrder.id)}
              >
                Mở cổng thanh toán VNPay Sandbox
              </Button>
              <Button
                block
                size="large"
                onClick={() => handleMockPayment(paymentModalOrder.id)}
              >
                Mô phỏng thanh toán Sandbox thành công ngay
              </Button>
            </Space>
          </div>
        )}
      </Modal>
    </div>
  );
}
