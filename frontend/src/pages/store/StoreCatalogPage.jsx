import { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Form, Input, InputNumber, Modal, Select, Space, Table, Tag, message } from 'antd';
import { HistoryOutlined, PlusOutlined } from '@ant-design/icons';
import { useAuth } from '../../auth/AuthProvider';
import { catalogApi } from '../../services/catalogApi';

const states = { AVAILABLE: 'Đang bán', SOLD_OUT: 'Hết hàng', CANCELLED: 'Đã hủy', EXPIRED: 'Hết hạn' };
const localInput = (value) => value ? value.slice(0, 16) : '';
const localDateTime = (value) => value && value.length === 16 ? `${value}:00` : value;

export default function StoreCatalogPage() {
  const { user } = useAuth();
  const owner = user?.role === 'STORE_OWNER';
  const [categories, setCategories] = useState([]);
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [editing, setEditing] = useState(null);
  const [editorOpen, setEditorOpen] = useState(false);
  const [stockBatch, setStockBatch] = useState(null);
  const [cancelBatch, setCancelBatch] = useState(null);
  const [ledgerBatch, setLedgerBatch] = useState(null);
  const [ledger, setLedger] = useState({ content: [], totalElements: 0 });
  const [ledgerPage, setLedgerPage] = useState(0);
  const [busy, setBusy] = useState(false);
  const [form] = Form.useForm();
  const [stockForm] = Form.useForm();
  const [cancelForm] = Form.useForm();

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const [categoryRows, batchRows] = await Promise.all([catalogApi.storeCategories(), catalogApi.storeBatches()]);
      setCategories(categoryRows.filter((category) => category.active));
      setRows(batchRows);
    } catch (failure) { setError(failure.message || 'Không tải được túi và danh mục'); }
    finally { setLoading(false); }
  }, []);

  useEffect(() => { load(); }, [load]);

  function showEditor(batch = null) {
    setEditing(batch);
    form.setFieldsValue(batch ? { ...batch, pickupStart: localInput(batch.pickupStart), pickupEnd: localInput(batch.pickupEnd) } : {
      categoryId: categories[0]?.id, title: '', description: '', imageUrl: '',
      originalPrice: 80000, rescuePrice: 40000, quantity: 1, pickupStart: '', pickupEnd: '',
    });
    setEditorOpen(true);
  }

  async function save(values) {
    setBusy(true);
    try {
      const request = { ...values, title: values.title.trim(), description: values.description?.trim() || null,
        imageUrl: values.imageUrl?.trim() || null, pickupStart: localDateTime(values.pickupStart),
        pickupEnd: localDateTime(values.pickupEnd), quantity: editing ? editing.quantity : values.quantity };
      if (editing) await catalogApi.updateBatch(editing.id, request);
      else await catalogApi.createBatch(request);
      setEditorOpen(false);
      await load();
      message.success(editing ? 'Đã cập nhật túi' : 'Đã tạo túi và ghi sổ kho');
    } catch (failure) { message.error(failure.message || 'Không lưu được túi'); }
    finally { setBusy(false); }
  }

  async function adjust(values) {
    setBusy(true);
    try {
      await catalogApi.adjustStock(stockBatch.id, values);
      setStockBatch(null);
      stockForm.resetFields();
      await load();
      message.success('Đã cập nhật tồn và ghi sổ kho');
    } catch (failure) { message.error(failure.message || 'Không điều chỉnh được tồn'); }
    finally { setBusy(false); }
  }

  async function cancel(values) {
    setBusy(true);
    try {
      await catalogApi.cancelBatch(cancelBatch.id, values.reason.trim());
      setCancelBatch(null);
      cancelForm.resetFields();
      await load();
      message.success('Đã hủy túi và ghi sổ kho');
    } catch (failure) { message.error(failure.message || 'Không hủy được túi'); }
    finally { setBusy(false); }
  }

  async function showLedger(batch, page = 0) {
    setLedgerBatch(batch);
    setLedgerPage(page);
    try {
      setLedger(await catalogApi.stockMovements(batch.id, { page, size: 20 }));
    } catch (failure) {
      setLedgerBatch(null);
      message.error(failure.message || 'Không tải được sổ kho');
    }
  }

  const columns = [
    { title: 'Túi', dataIndex: 'title' },
    { title: 'Danh mục', render: (_, row) => categories.find((category) => category.id === row.categoryId)?.name || 'Đã ẩn' },
    { title: 'Giá gốc / giá cứu', render: (_, row) => `${Number(row.originalPrice).toLocaleString('vi-VN')} / ${Number(row.rescuePrice).toLocaleString('vi-VN')} đ` },
    { title: 'Tồn', render: (_, row) => `${row.remainingQuantity} / ${row.quantity}` },
    { title: 'Giờ nhận', render: (_, row) => `${row.pickupStart?.replace('T', ' ').slice(0, 16)} – ${row.pickupEnd?.replace('T', ' ').slice(11, 16)}` },
    { title: 'Trạng thái', dataIndex: 'status', render: (value) => <Tag color={value === 'AVAILABLE' ? 'green' : value === 'CANCELLED' ? 'red' : 'default'}>{states[value] || value}</Tag> },
    { title: 'Thao tác', render: (_, row) => <Space wrap>
      <Button icon={<HistoryOutlined />} onClick={() => showLedger(row)}>Sổ kho</Button>
      <Button onClick={() => { stockForm.resetFields(); setStockBatch(row); }}>Chỉnh tồn</Button>
      {owner && row.status === 'AVAILABLE' && <Button onClick={() => showEditor(row)}>Sửa</Button>}
      {owner && !row.deleted && <Button danger onClick={() => Modal.confirm({
        title: `Hủy túi "${row.title}"?`,
        content: 'Tồn còn lại sẽ được ghi là REMOVED; túi được lưu lại và phát sự kiện hủy.',
        okText: 'Tiếp tục', cancelText: 'Quay lại', okButtonProps: { danger: true },
        onOk: () => { setCancelBatch(row); cancelForm.resetFields(); },
      })}>Hủy</Button>}
    </Space> },
  ];

  return <main style={{ maxWidth: 1280, margin: '0 auto', padding: 24 }}>
    <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 16 }}>
      <div><h1>Túi giải cứu của cửa hàng</h1><p>Điều chỉnh tồn luôn tạo một dòng sổ kho; không sửa/xóa lịch sử.</p></div>
      {owner && <Button type="primary" icon={<PlusOutlined />} disabled={!categories.length} onClick={() => showEditor()}>Tạo túi</Button>}
    </Space>
    {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}
    <Table rowKey="id" loading={loading} dataSource={rows} columns={columns} scroll={{ x: 1000 }} />

    <Modal title={editing ? 'Sửa túi' : 'Tạo túi'} open={editorOpen} onCancel={() => setEditorOpen(false)}
      onOk={() => form.submit()} confirmLoading={busy} destroyOnClose>
      <Form form={form} layout="vertical" onFinish={save}>
        <Form.Item name="categoryId" label="Danh mục" rules={[{ required: true }]}>
          <Select options={categories.map((category) => ({ value: category.id, label: category.name }))} />
        </Form.Item>
        <Form.Item name="title" label="Tên túi" rules={[{ required: true, whitespace: true }, { max: 150 }]}>
          <Input maxLength={150} />
        </Form.Item>
        <Form.Item name="description" label="Mô tả"><Input.TextArea rows={3} maxLength={2000} /></Form.Item>
        <Form.Item name="imageUrl" label="URL ảnh"><Input maxLength={255} /></Form.Item>
        <Form.Item name="originalPrice" label="Giá gốc" rules={[{ required: true }]}>
          <InputNumber min={1} precision={0} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="rescuePrice" label="Giá giải cứu (tối đa 60% giá gốc)" rules={[{ required: true }]}>
          <InputNumber min={1} precision={0} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="quantity" label="Số lượng ban đầu" rules={[{ required: true }]} extra={editing ? 'Tồn kho chỉ đổi qua nút Chỉnh tồn.' : null}>
          <InputNumber min={1} max={100000} precision={0} disabled={Boolean(editing)} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="pickupStart" label="Bắt đầu nhận" rules={[{ required: true }]}>
          <Input type="datetime-local" />
        </Form.Item>
        <Form.Item name="pickupEnd" label="Kết thúc nhận" rules={[{ required: true }]}>
          <Input type="datetime-local" />
        </Form.Item>
      </Form>
    </Modal>

    <Modal title={`Điều chỉnh tồn · ${stockBatch?.title || ''}`} open={Boolean(stockBatch)} onCancel={() => setStockBatch(null)}
      onOk={() => stockForm.submit()} confirmLoading={busy} destroyOnClose>
      <Form form={stockForm} layout="vertical" onFinish={adjust}>
        <Form.Item name="reason" label="Loại điều chỉnh" rules={[{ required: true }]}>
          <Select options={[{ value: 'ADDED', label: 'Thêm tồn' }, { value: 'REMOVED', label: 'Bớt tồn' }]} />
        </Form.Item>
        <Form.Item name="changeQty" label="Số lượng" rules={[{ required: true }]}>
          <InputNumber min={1} max={100000} precision={0} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="note" label="Ghi chú"><Input maxLength={255} /></Form.Item>
      </Form>
    </Modal>

    <Modal title={`Lý do hủy túi · ${cancelBatch?.title || ''}`} open={Boolean(cancelBatch)}
      onCancel={() => setCancelBatch(null)} onOk={() => cancelForm.submit()} confirmLoading={busy} destroyOnClose>
      <Form form={cancelForm} layout="vertical" onFinish={cancel}>
        <Form.Item name="reason" label="Lý do" rules={[{ required: true, whitespace: true }, { max: 255 }]}>
          <Input.TextArea rows={3} maxLength={255} />
        </Form.Item>
      </Form>
    </Modal>

    <Modal title={`Sổ kho · ${ledgerBatch?.title || ''}`} open={Boolean(ledgerBatch)}
      onCancel={() => setLedgerBatch(null)} footer={null} width={900}>
      <Table rowKey="id" dataSource={ledger.content} pagination={{
        current: ledgerPage + 1, pageSize: 20, total: ledger.totalElements, showSizeChanger: false,
        onChange: (current) => showLedger(ledgerBatch, current - 1),
      }} columns={[
        { title: 'Thời gian', dataIndex: 'createdAt', render: (value) => value?.replace('T', ' ').slice(0, 19) },
        { title: 'Loại', dataIndex: 'reason' },
        { title: 'Thay đổi', dataIndex: 'changeQty', render: (value) => value > 0 ? `+${value}` : value },
        { title: 'Tồn sau', dataIndex: 'remainingAfter' },
        { title: 'Nhân viên', dataIndex: 'actorId', render: (value) => value ? `#${value}` : 'Hệ thống' },
        { title: 'Ghi chú', dataIndex: 'note', ellipsis: true },
      ]} />
    </Modal>
  </main>;
}
