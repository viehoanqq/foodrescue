import { useEffect, useState } from 'react';
import { Card, Result, Button, Spin, Typography } from 'antd';
import { paymentApi } from './api';
import { formatMoney } from '../../shared/utils/format';

const { Text } = Typography;

export default function PaymentResultView({ onGoToOrders }) {
  const [loading, setLoading] = useState(true);
  const [result, setResult] = useState(null);

  useEffect(() => {
    const searchParams = new URLSearchParams(window.location.search);
    const params = Object.fromEntries(searchParams.entries());

    if (Object.keys(params).length === 0) {
      setLoading(false);
      setResult({ success: false, message: 'Không tìm thấy thông tin giao dịch VNPay' });
      return;
    }

    paymentApi
      .verifyPaymentReturn(params)
      .then((data) => {
        setResult(data);
      })
      .catch((err) => {
        setResult({ success: false, message: err.message || 'Lỗi xác thực chữ ký VNPay' });
      })
      .finally(() => {
        setLoading(false);
      });
  }, []);

  if (loading) {
    return (
      <Card style={{ maxWidth: 600, margin: '60px auto', textAlign: 'center', padding: 40 }}>
        <Spin size="large" />
        <div style={{ marginTop: 16 }}>
          <Text>Đang xác thực kết quả thanh toán từ VNPay Sandbox...</Text>
        </div>
      </Card>
    );
  }

  return (
    <Card style={{ maxWidth: 640, margin: '40px auto' }}>
      {result?.success ? (
        <Result
          status="success"
          title="Thanh toán đơn hàng thành công!"
          subTitle={
            <div>
              <p>Mã đơn hàng: <Text strong>{result.orderCode}</Text></p>
              <p>Số tiền đã thanh toán: <Text strong style={{ color: '#fa8c16' }}>{formatMoney(result.amount)}</Text></p>
              <p>Đơn hàng đã được xác nhận. Vui lòng kiểm tra mã nhận hàng và đến cửa hàng đúng khung giờ.</p>
            </div>
          }
          extra={[
            <Button
              type="primary"
              key="orders"
              size="large"
              style={{ background: '#fa8c16', borderColor: '#fa8c16' }}
              onClick={onGoToOrders}
            >
              Xem đơn hàng & Mã nhận hàng
            </Button>,
          ]}
        />
      ) : (
        <Result
          status="error"
          title="Thanh toán không thành công"
          subTitle={result?.message || 'Giao dịch bị huỷ hoặc có lỗi xảy ra.'}
          extra={[
            <Button type="primary" key="orders" onClick={onGoToOrders}>
              Quay lại danh sách đơn
            </Button>,
          ]}
        />
      )}
    </Card>
  );
}
