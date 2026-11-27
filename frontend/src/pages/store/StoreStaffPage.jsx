import { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Form, Input, Modal, Space, Table, Tag } from 'antd';
import { LockOutlined, PlusOutlined, UnlockOutlined } from '@ant-design/icons';
import { storeApi } from '../../services/storeApi';
import { confirmDialog, toast } from '../../components/feedback';
import './storePages.css';

export default function StoreStaffPage() {
  const [form] = Form.useForm();
  const [data, setData] = useState({ content: [], totalElements: 0 });
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [open, setOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [password, setPassword] = useState('');
  const [busyId, setBusyId] = useState(null);

  const load = useCallback(async (nextPage = page) => {
    setLoading(true); setError('');
    try { setData(await storeApi.staff({ page: nextPage, size: 10 })); }
    catch (failure) { setError(failure.message || 'Không tải được nhân viên'); }
    finally { setLoading(false); }
  }, [page]);
  useEffect(() => { load(); }, [load]);

  async function create(values) {
    setSaving(true);
    try {
      const created = await storeApi.createStaff(values);
      setOpen(false); form.resetFields(); setPassword(created.temporaryPassword);
      toast.success('Đã tạo tài khoản nhân viên');
      setPage(0); await load(0);
    } catch (failure) { toast.error(failure.message || 'Không tạo được nhân viên'); }
    finally { setSaving(false); }
  }

  function changeStatus(row) {
    const locked = row.status === 'ACTIVE';
    confirmDialog({ title: locked ? 'Khóa tài khoản nhân viên?' : 'Mở khóa tài khoản nhân viên?',
      content: `${row.fullName} (${row.email})`, okText: locked ? 'Khóa' : 'Mở khóa',
      danger: locked, tone: locked ? 'danger' : 'eco', cancelText: 'Hủy',
      async onOk() {
        setBusyId(row.id);
        try { await storeApi.setStaffLocked(row.id, locked); await load(); toast.success('Đã cập nhật trạng thái'); }
        catch (failure) { toast.error(failure.message || 'Không cập nhật được trạng thái'); throw failure; }
        finally { setBusyId(null); }
      },
    });
  }

  return <main className="store-page"><div className="store-page-title"><div><span>QUẢN LÝ CỬA HÀNG</span><h1>Nhân viên</h1><p>Tài khoản nhân viên thuộc cửa hàng của bạn.</p></div><Button type="primary" icon={<PlusOutlined />} onClick={() => setOpen(true)}>Thêm nhân viên</Button></div>
    {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}
    <Table rowKey="id" loading={loading} dataSource={data.content} pagination={{ current: page + 1, pageSize: 10, total: data.totalElements, showSizeChanger: false, onChange: (current) => setPage(current - 1) }} columns={[
      { title: 'Họ tên', dataIndex: 'fullName' }, { title: 'Email', dataIndex: 'email' },
      { title: 'Số điện thoại', dataIndex: 'phone' },
      { title: 'Trạng thái', dataIndex: 'status', render: (status) => <Tag color={status === 'ACTIVE' ? 'green' : 'red'}>{status === 'ACTIVE' ? 'Hoạt động' : 'Đã khóa'}</Tag> },
      { title: 'Thao tác', render: (_, row) => <Button loading={busyId === row.id} danger={row.status === 'ACTIVE'} icon={row.status === 'ACTIVE' ? <LockOutlined /> : <UnlockOutlined />} onClick={() => changeStatus(row)}>{row.status === 'ACTIVE' ? 'Khóa' : 'Mở khóa'}</Button> },
    ]} />
    <Modal title="Thêm nhân viên" open={open} onOk={() => form.submit()} okText="Tạo tài khoản" okButtonProps={{ loading: saving }} onCancel={() => { setOpen(false); form.resetFields(); }} destroyOnClose><Form form={form} layout="vertical" onFinish={create}>
      <Form.Item name="fullName" label="Họ tên" rules={[{ required: true, whitespace: true }, { max: 100 }]}><Input maxLength={100} /></Form.Item>
      <Form.Item name="email" label="Email" rules={[{ required: true, type: 'email' }, { max: 100 }]}><Input /></Form.Item>
      <Form.Item name="phone" label="Số điện thoại" rules={[{ required: true, pattern: /^0\d{9,10}$/, message: 'Nhập số điện thoại hợp lệ' }]}><Input maxLength={20} /></Form.Item>
    </Form></Modal>
    <Modal title="Mật khẩu tạm của nhân viên" open={Boolean(password)} onOk={() => setPassword('')} onCancel={() => setPassword('')} okText="Tôi đã lưu lại" cancelButtonProps={{ style: { display: 'none' } }} closable={false} maskClosable={false}><Alert type="warning" showIcon message="Mật khẩu này chỉ hiển thị một lần. Hãy chuyển riêng cho nhân viên và yêu cầu đổi ngay sau khi đăng nhập." /><p className="store-temp-password">{password}</p><Space><Button onClick={() => { navigator.clipboard.writeText(password); toast.success('Đã sao chép'); }}>Sao chép mật khẩu</Button></Space></Modal>
  </main>;
}
