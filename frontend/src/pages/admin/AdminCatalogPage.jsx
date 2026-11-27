import { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Form, Input, InputNumber, Modal, Select, Space, Table, Tabs, Tag, message } from 'antd';
import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import { catalogApi } from '../../services/catalogApi';
import { storeApi } from '../../services/storeApi';

export default function AdminCatalogPage() {
  const [stores, setStores] = useState([]);
  const [storeId, setStoreId] = useState(null);
  const [categories, setCategories] = useState([]);
  const [batches, setBatches] = useState({ content: [], totalElements: 0 });
  const [tab, setTab] = useState('categories');
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [editing, setEditing] = useState(null);
  const [editorOpen, setEditorOpen] = useState(false);
  const [cancelBatch, setCancelBatch] = useState(null);
  const [busy, setBusy] = useState(false);
  const [form] = Form.useForm();
  const [cancelForm] = Form.useForm();

  const loadStores = useCallback(async () => {
    try {
      const result = await storeApi.adminStores({ page: 0, size: 100 });
      setStores(result.content || []);
      if (result.content?.length) setStoreId((current) => current || result.content[0].id);
    } catch (failure) { setError(failure.message || 'Không tải được danh sách cửa hàng'); }
  }, []);

  const loadCatalog = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const [categoryRows, batchRows] = await Promise.all([
        storeId ? catalogApi.adminCategories(storeId) : Promise.resolve([]),
        catalogApi.adminBatches({ ...(storeId ? { storeId } : {}), page, size: 50 }),
      ]);
      setCategories(categoryRows);
      setBatches(batchRows);
    } catch (failure) { setError(failure.message || 'Không tải được dữ liệu catalog'); }
    finally { setLoading(false); }
  }, [storeId, page]);

  useEffect(() => { loadStores(); }, [loadStores]);
  useEffect(() => { loadCatalog(); }, [loadCatalog]);

  function showEditor(category = null) {
    setEditing(category);
    form.setFieldsValue(category || { name: '', description: '', imageUrl: '', sortOrder: 0 });
    setEditorOpen(true);
  }

  async function saveCategory(values) {
    setBusy(true);
    try {
      const body = { ...values, name: values.name.trim(),
        description: values.description?.trim() || null, imageUrl: values.imageUrl?.trim() || null };
      if (editing) await catalogApi.updateAdminCategory(storeId, editing.id, body);
      else await catalogApi.createAdminCategory(storeId, body);
      setEditorOpen(false);
      await loadCatalog();
      message.success('Đã lưu danh mục');
    } catch (failure) { message.error(failure.message || 'Không lưu được danh mục'); }
    finally { setBusy(false); }
  }

  async function archiveCategory(category) {
    setBusy(true);
    try {
      await catalogApi.archiveAdminCategory(storeId, category.id);
      await loadCatalog();
      message.success('Đã ẩn danh mục; batch và sổ kho được giữ lại');
    } catch (failure) { message.error(failure.message || 'Không thể ẩn danh mục'); }
    finally { setBusy(false); }
  }

  async function doCancelBatch(values) {
    setBusy(true);
    try {
      await catalogApi.cancelAdminBatch(cancelBatch.id, values.reason.trim());
      setCancelBatch(null);
      cancelForm.resetFields();
      await loadCatalog();
      message.success('Đã hủy túi và ghi sổ kho');
    } catch (failure) { message.error(failure.message || 'Không hủy được túi'); }
    finally { setBusy(false); }
  }

  const categoryColumns = [
    { title: 'Tên danh mục', dataIndex: 'name' },
    { title: 'Mô tả', dataIndex: 'description', ellipsis: true },
    { title: 'Trạng thái', render: (_, row) => <Tag color={row.active ? 'green' : 'default'}>{row.active ? 'Đang dùng' : 'Đã ẩn'}</Tag> },
    { title: 'Thao tác', render: (_, row) => row.active && <Space>
      <Button icon={<EditOutlined />} onClick={() => showEditor(row)}>Sửa</Button>
      <Button danger icon={<DeleteOutlined />} onClick={() => Modal.confirm({
        title: `Ẩn danh mục "${row.name}"?`,
        content: 'Batch đang tham chiếu và lịch sử kho vẫn được giữ nguyên.',
        okText: 'Ẩn', cancelText: 'Hủy', okButtonProps: { danger: true },
        onOk: () => archiveCategory(row),
      })}>Ẩn</Button>
    </Space> },
  ];

  const batchColumns = [
    { title: 'ID', dataIndex: 'id' },
    { title: 'Tên túi', dataIndex: 'title' },
    { title: 'Danh mục', render: (_, row) => categories.find((category) => category.id === row.categoryId)?.name || 'Đã ẩn' },
    { title: 'Cửa hàng', dataIndex: 'storeId' },
    { title: 'Tồn', render: (_, row) => `${row.remainingQuantity} / ${row.quantity}` },
    { title: 'Trạng thái', dataIndex: 'status' },
    { title: 'Thao tác', render: (_, row) => !row.deleted && <Button danger disabled={row.status === 'CANCELLED'}
      onClick={() => { setCancelBatch(row); cancelForm.resetFields(); }}>Hủy túi</Button> },
  ];

  return <main style={{ maxWidth: 1280, margin: '0 auto', padding: 24 }}>
    <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 16 }}>
      <div><h1>Quản lý danh mục và túi</h1><p>Thao tác được lưu trong Catalog; xóa danh mục là ẩn mềm.</p></div>
      <Select allowClear showSearch optionFilterProp="label" placeholder="Tất cả cửa hàng"
        value={storeId} style={{ width: 300 }} onChange={(value) => { setStoreId(value || null); setPage(0); }}
        options={stores.map((store) => ({ value: store.id, label: `${store.name} (#${store.id})` }))} />
    </Space>
    {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}
    <Tabs activeKey={tab} onChange={setTab} items={[
      { key: 'categories', label: 'Danh mục', children: <>
        <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 12 }}>
          <Button type="primary" icon={<PlusOutlined />} disabled={!storeId} onClick={() => showEditor()}>Tạo danh mục</Button>
        </div>
        <Table rowKey="id" loading={loading} dataSource={categories} columns={categoryColumns} />
      </> },
      { key: 'batches', label: 'Túi giải cứu', children: <Table rowKey="id" loading={loading}
        dataSource={batches.content} pagination={{ current: page + 1, pageSize: 50, total: batches.totalElements,
          showSizeChanger: false, onChange: (current) => setPage(current - 1) }}
        columns={batchColumns} scroll={{ x: 900 }} /> },
    ]} />
    <Modal title={editing ? 'Sửa danh mục' : 'Tạo danh mục'} open={editorOpen} onCancel={() => setEditorOpen(false)}
      onOk={() => form.submit()} confirmLoading={busy} destroyOnClose>
      <Form form={form} layout="vertical" onFinish={saveCategory}>
        <Form.Item name="name" label="Tên danh mục" rules={[{ required: true, whitespace: true }, { max: 100 }]}><Input maxLength={100} /></Form.Item>
        <Form.Item name="description" label="Mô tả" rules={[{ max: 1000 }]}><Input.TextArea rows={3} maxLength={1000} /></Form.Item>
        <Form.Item name="imageUrl" label="URL ảnh" rules={[{ max: 255 }]}><Input maxLength={255} /></Form.Item>
        <Form.Item name="sortOrder" label="Thứ tự"><InputNumber min={0} max={9999} style={{ width: '100%' }} /></Form.Item>
      </Form>
    </Modal>
    <Modal title={`Hủy túi "${cancelBatch?.title || ''}"?`} open={Boolean(cancelBatch)} onCancel={() => setCancelBatch(null)}
      onOk={() => cancelForm.submit()} confirmLoading={busy} destroyOnClose>
      <p>Tồn còn lại sẽ được ghi là REMOVED. Túi, reservations và lịch sử vẫn được lưu.</p>
      <Form form={cancelForm} layout="vertical" onFinish={doCancelBatch}>
        <Form.Item name="reason" label="Lý do hủy" rules={[{ required: true, whitespace: true }, { max: 255 }]}>
          <Input.TextArea maxLength={255} rows={3} />
        </Form.Item>
      </Form>
    </Modal>
  </main>;
}
