import { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Form, Input, InputNumber, Modal, Space, Table, Tag, message } from 'antd';
import { DeleteOutlined, EditOutlined, PlusOutlined } from '@ant-design/icons';
import { useAuth } from '../../auth/AuthProvider';
import { catalogApi } from '../../services/catalogApi';

export default function CatalogCategoriesPage() {
  const { user } = useAuth();
  const owner = user?.role === 'STORE_OWNER';
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [editing, setEditing] = useState(null);
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [form] = Form.useForm();

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try { setRows(await catalogApi.storeCategories()); }
    catch (failure) { setError(failure.message || 'Không tải được danh mục'); }
    finally { setLoading(false); }
  }, []);

  useEffect(() => { load(); }, [load]);

  function showEditor(category = null) {
    setEditing(category);
    form.setFieldsValue(category || { name: '', description: '', imageUrl: '', sortOrder: 0 });
    setOpen(true);
  }

  async function save(values) {
    setBusy(true);
    try {
      const request = { ...values, name: values.name.trim(), description: values.description?.trim() || null,
        imageUrl: values.imageUrl?.trim() || null };
      if (editing) await catalogApi.updateCategory(editing.id, request);
      else await catalogApi.createCategory(request);
      setOpen(false);
      form.resetFields();
      await load();
      message.success(editing ? 'Đã cập nhật danh mục' : 'Đã tạo danh mục');
    } catch (failure) { message.error(failure.message || 'Không lưu được danh mục'); }
    finally { setBusy(false); }
  }

  async function archive(category) {
    setBusy(true);
    try {
      await catalogApi.archiveCategory(category.id);
      await load();
      message.success('Đã ẩn danh mục; các túi và sổ kho được giữ nguyên');
    } catch (failure) { message.error(failure.message || 'Không thể ẩn danh mục'); }
    finally { setBusy(false); }
  }

  return <main style={{ maxWidth: 1180, margin: '0 auto', padding: 24 }}>
    <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 16 }}>
      <div><h1>Danh mục cửa hàng</h1><p>Danh mục thuộc cửa hàng của bạn; ẩn danh mục không xóa túi hay lịch sử tồn kho.</p></div>
      {owner && <Button type="primary" icon={<PlusOutlined />} onClick={() => showEditor()}>Thêm danh mục</Button>}
    </Space>
    {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}
    <Table rowKey="id" loading={loading} dataSource={rows} columns={[
      { title: 'Tên danh mục', dataIndex: 'name' },
      { title: 'Mô tả', dataIndex: 'description', ellipsis: true },
      { title: 'Thứ tự', dataIndex: 'sortOrder' },
      { title: 'Trạng thái', render: (_, row) => <Tag color={row.active ? 'green' : 'default'}>{row.active ? 'Đang dùng' : 'Đã ẩn'}</Tag> },
      ...(owner ? [{ title: 'Thao tác', render: (_, row) => row.active && <Space>
        <Button icon={<EditOutlined />} onClick={() => showEditor(row)}>Sửa</Button>
        <Button danger icon={<DeleteOutlined />} onClick={() => Modal.confirm({
          title: `Ẩn danh mục "${row.name}"?`,
          content: 'Các túi hiện có và sổ kho vẫn được giữ nguyên; không thể dùng danh mục này cho túi mới.',
          okText: 'Ẩn danh mục', cancelText: 'Quay lại', okButtonProps: { danger: true },
          onOk: () => archive(row),
        })}>Ẩn</Button>
      </Space> }] : []),
    ]} />
    <Modal title={editing ? 'Sửa danh mục' : 'Tạo danh mục'} open={open} onCancel={() => setOpen(false)}
      onOk={() => form.submit()} confirmLoading={busy} destroyOnClose>
      <Form form={form} layout="vertical" onFinish={save}>
        <Form.Item name="name" label="Tên danh mục" rules={[{ required: true, whitespace: true }, { max: 100 }]}>
          <Input maxLength={100} />
        </Form.Item>
        <Form.Item name="description" label="Mô tả" rules={[{ max: 1000 }]}>
          <Input.TextArea rows={3} maxLength={1000} />
        </Form.Item>
        <Form.Item name="imageUrl" label="URL ảnh" rules={[{ max: 255 }]}>
          <Input maxLength={255} />
        </Form.Item>
        <Form.Item name="sortOrder" label="Thứ tự" rules={[{ type: 'number', min: 0, max: 9999 }]}>
          <InputNumber min={0} max={9999} style={{ width: '100%' }} />
        </Form.Item>
      </Form>
    </Modal>
  </main>;
}
