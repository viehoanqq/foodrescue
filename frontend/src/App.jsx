import { useEffect, useState } from 'react';
import { Button, Card, Result } from 'antd';
import { api } from './shared/api/axios';
import { formatMoney } from './shared/utils/format';

export default function App() {
  const [ping, setPing] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    api.get('/ping').then(setPing).catch(setError);
  }, []);

  return (
    <Card style={{ maxWidth: 480, margin: '64px auto' }}>
      {ping ? (
        <Result status="success" title="Frontend đã nối được backend"
          subTitle={`Máy chủ trả: ${ping.status} · Thử format: ${formatMoney(45000)}`} />
      ) : (
        <Result status="warning" title="Chưa nối được backend"
          subTitle={error?.message ?? 'Đang kết nối...'} />
      )}
      <Button type="primary" block>Nút màu cam của theme</Button>
    </Card>
  );
}
