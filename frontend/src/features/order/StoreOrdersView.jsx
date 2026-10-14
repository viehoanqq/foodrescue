import { useEffect, useState, useCallback } from 'react';
import { Card, Table, Tag, Button, Space, Modal, Typography, Radio, message, Input, List, Result } from 'antd';
import { CloseCircleOutlined, EyeOutlined, ShopOutlined, UserSwitchOutlined } from '@ant-design/icons';
import { storeOrderApi } from './api';
import { formatMoney, formatDateTime, formatPickupWindow } from '../../shared/utils/format';
import { ORDER_STATUS, PAYMENT_STATUS } from '../../shared/constants/status';

const { Title, Text } = Typography;

export default function StoreOrdersView({ currentUser, onSwitchUser }) {
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(false);
  const [filterStatus, setFilterStatus] = useState(null);
  const [selectedOrder, setSelectedOrder] = useState(null);
  const [cancelModalOrder, setCancelModalOrder] = useState(null);
  const [cancelReason, setCancelReason] = useState('');
  const [cancelling, setCancelling] = useState(false);

  const isCustomer = currentUser?.role === 'CUSTOMER';

  const loadOrders = useCallback(async (status) => {
    if (isCustomer) return;
    setLoading(true);
    try {
      const data = await storeOrderApi.getStoreOrders(status);
      setOrders(data);
    } catch (err) {
      message.error(err.message || 'Không thể tải danh sách đơn hàng của cửa hàng');
    } finally {
      setLoading(false);
    }
  }, [isCustomer]);

  useEffect(() => {
    if (!isCustomer) {
      loadOrders(filterStatus);
    }
  }, [filterStatus, isCustomer, loadOrders]);

  const handleCancelOrder = async () => {
    if (!cancelModalOrder || !cancelReason.trim()) {
      message.warning('Vui lòng nhập lý do huỷ đơn');
      return;
    }
    setCancelling(true);
    try {
      await storeOrderApi.cancelOrder(cancelModalOrder.id, cancelReason.trim());
      message.success(`Đã huỷ đơn ${cancelModalOrder.orderCode}. Túi đã được hoàn lại kho.`);
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
      title: 'Khách hàng',
      key: 'customer',
      render: (_, record) => (
        <div>
          <Text strong>{record.customerName}</Text>
          <br />
          <Text type="secondary" style={{ fontSize: 12 }}>{record.customerPhone}</Text>
        </div>
      ),
    },
    {
      title: 'Khung giờ nhận',
      key: 'pickupWindow',
      render: (_, record) => (
        <div>
          <Tag color="cyan">{formatPickupWindow(record.pickupStart, record.pickupEnd)}</Tag>
          <br />
          <Text type="secondary" style={{ fontSize: 12 }}>Mã nhận: <Text strong>{record.pickupCode}</Text></Text>
        </div>
      ),
    },
    {
      title: 'Số túi',
      dataIndex: 'itemCount',
      key: 'itemCount',
      render: (cnt) => `${cnt} túi`,
    },
    {
      title: 'Cửa hàng thực nhận',
      key: 'money',
      render: (_, record) => (
        <div>
          <Text strong style={{ color: '#52c41a' }}>{formatMoney(record.storeEarning)}</Text>
          <br />
          <Text type="secondary" style={{ fontSize: 11 }}>Tổng đơn: {formatMoney(record.totalAmount)}</Text>
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
        <Space>
          <Button
            size="small"
            icon={<EyeOutlined />}
            onClick={() => setSelectedOrder(record)}
          >
            Chi tiết
          </Button>
          {(record.status === 'CONFIRMED' || record.status === 'PENDING_PAYMENT') && (
            <Button
              size="small"
              danger
              icon={<CloseCircleOutlined />}
              onClick={() => {
                setCancelModalOrder(record);
                setCancelReason('');
              }}
            >
              Huỷ đơn
            </Button>
          )}
        </Space>
      ),
    },
  ];

  if (isCustomer) {
    return (
      <div style={{ maxWidth: 800, margin: '20px auto' }}>
        <Card>
          <Result
            status="info"
            title="Bạn đang ở vai trò Khách hàng"
            subTitle={`Tài khoản hiện tại là "${currentUser?.name}". Tab "Đơn của cửa hàng" chỉ dành cho Nhân viên hoặc Chủ cửa hàng để quản lý và huỷ đơn của quán mình.`}
            extra={[
              <Button
                type="primary"
                key="staff"
                icon={<UserSwitchOutlined />}
                style={{ background: '#fa8c16', borderColor: '#fa8c16' }}
                onClick={() => onSwitchUser && onSwitchUser('6')}
              >
                Chuyển sang Đỗ Minh Khoa (Nhân viên An Phát)
              </Button>,
              <Button
                key="owner"
                icon={<UserSwitchOutlined />}
                onClick={() => onSwitchUser && onSwitchUser('3')}
              >
                Chuyển sang Lê Thị Thu Thảo (Chủ Sweet Paris)
              </Button>,
            ]}
          />
        </Card>
      </div>
    );
  }

  return (
    <div style={{ maxWidth: 1050, margin: '0 auto', padding: '16px 0' }}>
      <Card
        title={
          <Space>
            <ShopOutlined style={{ fontSize: 22, color: '#fa8c16' }} />
            <span>Quản lý đơn hàng của cửa hàng ({currentUser?.store || 'Cửa hàng'})</span>
          </Space>
        }
      >
        <div style={{ marginBottom: 16 }}>
          <Radio.Group
            value={filterStatus}
            onChange={(e) => setFilterStatus(e.target.value)}
            buttonStyle="solid"
          >
            <Radio.Button value={null}>Tất cả</Radio.Button>
            <Radio.Button value="CONFIRMED">Chờ nhận hàng</Radio.Button>
            <Radio.Button value="COMPLETED">Đã giao</Radio.Button>
            <Radio.Button value="NO_SHOW">Không đến lấy</Radio.Button>
            <Radio.Button value="CANCELLED">Đã huỷ</Radio.Button>
            <Radio.Button value="PENDING_PAYMENT">Chờ thanh toán</Radio.Button>
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

      {/* Modal chi tiết đơn */}
      <Modal
        title={`Chi tiết đơn hàng ${selectedOrder?.orderCode}`}
        open={!!selectedOrder}
        onCancel={() => setSelectedOrder(null)}
        footer={[
          <Button key="close" onClick={() => setSelectedOrder(null)}>
            Đóng
          </Button>,
        ]}
        width={600}
      >
        {selectedOrder && (
          <div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginBottom: 16 }}>
              <div><Text type="secondary">Khách hàng: </Text><Text strong>{selectedOrder.customerName}</Text></div>
              <div><Text type="secondary">Điện thoại: </Text><Text strong>{selectedOrder.customerPhone}</Text></div>
              <div><Text type="secondary">Mã nhận hàng: </Text><Tag color="blue">{selectedOrder.pickupCode}</Tag></div>
              <div><Text type="secondary">Giờ nhận: </Text><Tag color="cyan">{formatPickupWindow(selectedOrder.pickupStart, selectedOrder.pickupEnd)}</Tag></div>
              <div><Text type="secondary">Tổng thanh toán: </Text><Text strong>{formatMoney(selectedOrder.totalAmount)}</Text></div>
              <div><Text type="secondary">Cửa hàng thực nhận: </Text><Text strong style={{ color: '#52c41a' }}>{formatMoney(selectedOrder.storeEarning)}</Text></div>
            </div>

            {selectedOrder.cancelReason && (
              <div style={{ marginBottom: 16, padding: 10, background: '#fff1f0', border: '1px solid #ffa39e', borderRadius: 6 }}>
                <Text type="danger" strong>Lý do huỷ: </Text>
                <Text>{selectedOrder.cancelReason}</Text>
              </div>
            )}

            <Title level={5}>Các túi trong đơn:</Title>
            <List
              bordered
              dataSource={selectedOrder.items || []}
              renderItem={(it) => (
                <List.Item>
                  <List.Item.Meta
                    title={it.title}
                    description={`Đơn giá: ${formatMoney(it.unitPrice)} · Số lượng: ${it.quantity}`}
                  />
                  <Text strong>{formatMoney(it.subtotal)}</Text>
                </List.Item>
              )}
            />
          </div>
        )}
      </Modal>

      {/* Modal cửa hàng huỷ đơn */}
      <Modal
        title={`Huỷ đơn hàng ${cancelModalOrder?.orderCode}`}
        open={!!cancelModalOrder}
        onCancel={() => setCancelModalOrder(null)}
        onOk={handleCancelOrder}
        confirmLoading={cancelling}
        okText="Xác nhận huỷ đơn"
        okButtonProps={{ danger: true }}
      >
        <p>Cửa hàng huỷ đơn hàng này sẽ thực hiện:</p>
        <ul style={{ paddingLeft: 20 }}>
          <li>Hoàn trả lại số lượng túi về kho để tiếp tục bán.</li>
          {cancelModalOrder?.paymentStatus === 'PAID' && (
            <li style={{ color: '#cf1322' }}>
              Đơn đã thanh toán: Hệ thống sẽ tự động tạo yêu cầu hoàn tiền (REFUND_PENDING) cho khách hàng.
            </li>
          )}
        </ul>
        <Text strong>Vui lòng nhập lý do huỷ đơn:</Text>
        <Input.TextArea
          placeholder="VD: Hết bánh mì mini tại quầy, nguyên liệu gặp sự cố..."
          value={cancelReason}
          onChange={(e) => setCancelReason(e.target.value)}
          rows={3}
          style={{ marginTop: 8 }}
        />
      </Modal>
    </div>
  );
}
