import { useState } from 'react';
import { Card, Input, Button, Space, Typography, Tag, Alert, message, Result, List, Divider } from 'antd';
import { SearchOutlined, CheckCircleOutlined, ShopOutlined, UserOutlined, PhoneOutlined, UserSwitchOutlined } from '@ant-design/icons';
import { pickupApi } from './api';
import { formatMoney, formatPickupWindow } from '../../shared/utils/format';

const { Title, Text } = Typography;

export default function StorePickupView({ currentUser, onSwitchUser }) {
  const [code, setCode] = useState('');
  const [loading, setLoading] = useState(false);
  const [order, setOrder] = useState(null);
  const [handingOver, setHandingOver] = useState(false);
  const [successResult, setSuccessResult] = useState(null);

  const isCustomer = currentUser?.role === 'CUSTOMER';

  const handleLookup = async () => {
    if (!code || !code.trim()) {
      message.warning('Vui lòng nhập mã nhận hàng');
      return;
    }
    setLoading(true);
    setSuccessResult(null);
    try {
      const data = await pickupApi.lookup(code.trim().toUpperCase());
      setOrder(data);
    } catch (err) {
      setOrder(null);
      message.error(err.message || 'Mã nhận hàng không đúng hoặc không thuộc cửa hàng này');
    } finally {
      setLoading(false);
    }
  };

  const handleHandover = async () => {
    if (!order) return;
    setHandingOver(true);
    try {
      const res = await pickupApi.handover(order.pickupCode);
      message.success(`Đã xác nhận giao đơn ${res.orderCode} thành công!`);
      setSuccessResult(res);
      setOrder(null);
      setCode('');
    } catch (err) {
      message.error(err.message || 'Không thể xác nhận giao hàng');
    } finally {
      setHandingOver(false);
    }
  };

  if (isCustomer) {
    return (
      <div style={{ maxWidth: 800, margin: '20px auto' }}>
        <Card>
          <Result
            status="info"
            title="Bạn đang ở vai trò Khách hàng"
            subTitle={`Tài khoản hiện tại là "${currentUser?.name}". Màn hình quầy chỉ dành cho Nhân viên hoặc Chủ cửa hàng để tra cứu mã và giao hàng cho khách.`}
            extra={[
              <Button
                type="primary"
                key="staff"
                icon={<UserSwitchOutlined />}
                style={{ background: '#1890ff', borderColor: '#1890ff' }}
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
    <div style={{ maxWidth: 720, margin: '0 auto', padding: '16px 0' }}>
      <Card
        title={
          <Space>
            <ShopOutlined style={{ fontSize: 22, color: '#1890ff' }} />
            <span>Màn hình quầy - Giao hàng bằng mã nhận hàng</span>
          </Space>
        }
      >
        <div style={{ marginBottom: 24, textAlign: 'center' }}>
          <Text type="secondary">Nhập mã nhận hàng 6 ký tự mà khách hàng cung cấp tại quầy:</Text>
          <div style={{ display: 'flex', gap: 8, maxWidth: 400, margin: '12px auto' }}>
            <Input
              size="large"
              placeholder="VD: H2R9W7"
              value={code}
              onChange={(e) => setCode(e.target.value.toUpperCase())}
              onPressEnter={handleLookup}
              style={{ textAlign: 'center', letterSpacing: 4, fontWeight: 'bold', fontSize: 20 }}
              maxLength={10}
            />
            <Button
              type="primary"
              size="large"
              icon={<SearchOutlined />}
              loading={loading}
              onClick={handleLookup}
            >
              Tra cứu
            </Button>
          </div>
        </div>

        {order && (
          <div style={{ border: '1px solid #d9d9d9', borderRadius: 8, padding: 20, background: '#fafafa' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <Text type="secondary">Mã đơn hàng: </Text>
                <Text strong>{order.orderCode}</Text>
              </div>
              <Tag color="blue" style={{ fontSize: 16, padding: '4px 12px' }}>
                MÃ: {order.pickupCode}
              </Tag>
            </div>

            <Divider style={{ margin: '12px 0' }} />

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, marginBottom: 16 }}>
              <div>
                <Text type="secondary"><UserOutlined /> Khách hàng: </Text>
                <Text strong>{order.customerName}</Text>
              </div>
              <div>
                <Text type="secondary"><PhoneOutlined /> Số điện thoại: </Text>
                <Text strong>{order.customerPhone}</Text>
              </div>
              <div>
                <Text type="secondary">Khung giờ nhận: </Text>
                <Tag color="cyan">{formatPickupWindow(order.pickupStart, order.pickupEnd)}</Tag>
              </div>
              <div>
                <Text type="secondary">Tổng tiền: </Text>
                <Text strong style={{ color: '#fa8c16' }}>{formatMoney(order.totalAmount)}</Text>
              </div>
            </div>

            {order.early && (
              <Alert
                type="warning"
                showIcon
                message="Khách hàng đến sớm hơn khung giờ nhận"
                description="Khung giờ nhận hàng bắt đầu lúc giờ quy định. Bạn vẫn có thể giao nếu cửa hàng đã chuẩn bị xong."
                style={{ marginBottom: 16 }}
              />
            )}

            {order.late && (
              <Alert
                type="error"
                showIcon
                message="Đã quá khung giờ nhận hàng"
                style={{ marginBottom: 16 }}
              />
            )}

            <Title level={5} style={{ marginTop: 16 }}>Danh sách túi cần soạn giao cho khách:</Title>
            <List
              bordered
              dataSource={order.items || []}
              renderItem={(it) => (
                <List.Item>
                  <List.Item.Meta
                    title={<Text strong>{it.title}</Text>}
                    description={`Đơn giá: ${formatMoney(it.unitPrice)}`}
                  />
                  <div>
                    <Tag color="orange" style={{ fontSize: 14, padding: '4px 10px' }}>
                      Số lượng: {it.quantity} túi
                    </Tag>
                  </div>
                </List.Item>
              )}
              style={{ background: '#fff', marginBottom: 20 }}
            />

            <Button
              type="primary"
              size="large"
              block
              icon={<CheckCircleOutlined />}
              loading={handingOver}
              onClick={handleHandover}
              style={{ background: '#52c41a', borderColor: '#52c41a', height: 48, fontSize: 16 }}
            >
              Xác nhận đã giao hàng cho khách
            </Button>
          </div>
        )}

        {successResult && (
          <Result
            status="success"
            title="Giao hàng thành công!"
            subTitle={`Đơn hàng ${successResult.orderCode} đã chuyển sang trạng thái ĐÃ NHẬN HÀNG (COMPLETED).`}
            extra={[
              <Button type="primary" key="next" onClick={() => setSuccessResult(null)}>
                Tiếp tục phục vụ khách tiếp theo
              </Button>,
            ]}
          />
        )}
      </Card>
    </div>
  );
}
