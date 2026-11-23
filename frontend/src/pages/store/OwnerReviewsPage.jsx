import { useEffect, useState } from 'react';
import { Alert, Spin } from 'antd';
import { storeApi } from '../../services/storeApi';
import StoreReviews from './StoreReviews';
import './storePages.css';

export default function OwnerReviewsPage() {
  const [storeId, setStoreId] = useState(null);
  const [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    storeApi.profile().then((store) => { if (active) setStoreId(store.id); })
      .catch((failure) => { if (active) setError(failure.message || 'Không tải được cửa hàng'); });
    return () => { active = false; };
  }, []);
  return <main className="store-page"><div className="store-page-heading"><span>CỬA HÀNG CỦA BẠN</span><h1>Đánh giá</h1><p>Ý kiến của khách hàng sau khi nhận đơn.</p></div>
    {error ? <Alert type="error" showIcon message={error} /> : storeId ? <StoreReviews storeId={storeId} /> : <div className="store-center"><Spin /></div>}
  </main>;
}
