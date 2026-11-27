import { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Card, Empty, Form, Input, InputNumber, Pagination, Rate, Spin } from 'antd';
import { Link } from 'react-router-dom';
import { useAuth } from '../../auth/AuthProvider';
import { storeApi } from '../../services/storeApi';
import { confirmDialog, toast } from '../../components/feedback';

export default function StoreReviews({ storeId, allowCreate = false, compact = false }) {
  const { user } = useAuth();
  const [form] = Form.useForm();
  const [page, setPage] = useState(0);
  const [data, setData] = useState({ content: [], averageRating: 0, totalElements: 0 });
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [formOpen, setFormOpen] = useState(!compact);
  const pageSize = compact ? 3 : 10;

  const load = useCallback(async () => {
    if (!storeId) return;
    setLoading(true);
    setError('');
    try { setData(await storeApi.reviews(storeId, { page, size: pageSize })); }
    catch (failure) { setError(failure.message || 'Không tải được đánh giá'); }
    finally { setLoading(false); }
  }, [storeId, page, pageSize]);

  useEffect(() => { load(); }, [load]);

  async function submit(values) {
    setSaving(true);
    try {
      await storeApi.createReview(storeId, { ...values, comment: values.comment?.trim() || null });
      form.resetFields();
      if (compact) setFormOpen(false);
      toast.success('Đã gửi đánh giá');
      if (page === 0) await load();
      else setPage(0);
    } catch (failure) { toast.error(failure.message || 'Không gửi được đánh giá'); }
    finally { setSaving(false); }
  }

  return <section className={`store-reviews ${compact ? 'store-reviews-compact' : ''}`}>
    <div className="store-reviews-heading"><div><h2>Đánh giá cửa hàng</h2>{!loading && <span>{data.averageRating}/5 · {data.totalElements} đánh giá</span>}</div>
      {compact && allowCreate && user?.role === 'CUSTOMER' && <Button onClick={() => setFormOpen(open => !open)}>{formOpen ? 'Đóng' : 'Viết đánh giá'}</Button>}
    </div>
    {allowCreate && user?.role === 'CUSTOMER' && formOpen && <Card className="store-card" title="Đánh giá đơn đã hoàn thành">
      <p>Nhập mã đơn đã hoàn thành tại cửa hàng này. Mỗi đơn chỉ được đánh giá một lần.</p>
      <Form form={form} layout="vertical" onFinish={(values) => confirmDialog({ title: 'Gửi đánh giá cửa hàng?',
        content: `Đánh giá ${values.rating} sao cho đơn #${values.orderId}. Mỗi đơn chỉ được đánh giá một lần.`,
        okText: 'Gửi đánh giá', cancelText: 'Kiểm tra lại', tone: 'eco', onOk: () => submit(values) })}>
        <Form.Item name="orderId" label="Mã đơn hàng" rules={[{ required: true, message: 'Nhập mã đơn hàng' }]}><InputNumber min={1} precision={0} style={{ width: '100%' }} /></Form.Item>
        <Form.Item name="rating" label="Số sao" rules={[{ required: true, message: 'Chọn số sao' }]}><Rate /></Form.Item>
        <Form.Item name="comment" label="Nhận xét" rules={[{ max: 1000, message: 'Tối đa 1000 ký tự' }]}><Input.TextArea rows={3} maxLength={1000} showCount /></Form.Item>
        <Button type="primary" htmlType="submit" loading={saving}>Gửi đánh giá</Button>
      </Form>
    </Card>}
    {allowCreate && !user && <p><Link to="/login">Đăng nhập</Link> để đánh giá đơn hàng đã hoàn thành.</p>}
    {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} action={<Button onClick={load}>Thử lại</Button>} />}
    {loading ? <div className="store-center"><Spin /></div> : !error && (data.content.length ? <>
      {data.content.map((review) => <Card size={compact ? 'small' : 'default'} className="store-card" key={review.id}>
        <Rate disabled value={review.rating} /><span className="store-review-date">{review.createdAt?.replace('T', ' ').slice(0, 16)}</span>
        <p>Đơn #{review.orderId}</p>{review.comment && <p>{review.comment}</p>}
      </Card>)}
      <Pagination current={page + 1} pageSize={pageSize} total={data.totalElements} showSizeChanger={false} onChange={(next) => setPage(next - 1)} />
    </> : <Empty description="Chưa có đánh giá" />)}
  </section>;
}
