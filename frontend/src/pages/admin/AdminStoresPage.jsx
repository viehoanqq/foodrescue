import { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Col, Descriptions, Form, Image, Input, InputNumber, Modal, Row, Select, Space, Table, Tag, TimePicker, message } from 'antd';
import { CheckOutlined, EyeOutlined, CloseOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { storeApi } from '../../services/storeApi';
import '../store/storePages.css';

const states = { PENDING: 'Chờ duyệt', APPROVED: 'Đã duyệt', REJECTED: 'Từ chối', LOCKED: 'Đã khóa' };

export default function AdminStoresPage() {
  const [data, setData] = useState({ content: [], totalElements: 0 });
  const [status, setStatus] = useState(null);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [selected, setSelected] = useState(null);
  const [licenseUrl, setLicenseUrl] = useState('');
  const [busy, setBusy] = useState(false);
  const [rejectOpen, setRejectOpen] = useState(false);
  const [rejectForm] = Form.useForm();
  const [lockOpen, setLockOpen] = useState(false);
  const [lockForm] = Form.useForm();
  const [editOpen, setEditOpen] = useState(false);
  const [editForm] = Form.useForm();

  const load = useCallback(async () => {
    setLoading(true); setError('');
    try { setData(await storeApi.adminStores({ ...(status ? { status } : {}), page, size: 10 })); }
    catch (failure) { setError(failure.message || 'Không tải được cửa hàng'); }
    finally { setLoading(false); }
  }, [status, page]);
  useEffect(() => { load(); }, [load]);
  useEffect(() => () => { if (licenseUrl) URL.revokeObjectURL(licenseUrl); }, [licenseUrl]);

  async function show(id) {
    setError('');
    setLicenseUrl('');
    try {
      const result = await storeApi.adminStore(id);
      setSelected(result);
      try { const blob = await storeApi.adminLicense(id); setLicenseUrl(URL.createObjectURL(blob)); }
      catch (failure) { message.warning(failure.message || 'Không tải được ảnh giấy phép'); }
    } catch (failure) { setError(failure.message || 'Không tải được hồ sơ'); }
  }

  async function approve() {
    setBusy(true);
    try {
      const updated = await storeApi.approve(selected.id, Number(selected.commissionRate ?? 15));
      setSelected(updated); await load(); message.success('Đã duyệt cửa hàng. Quyền chủ sẽ được cập nhật qua RabbitMQ.');
    } catch (failure) { message.error(failure.message || 'Không duyệt được cửa hàng'); }
    finally { setBusy(false); }
  }

  async function reject(values) {
    setBusy(true);
    try { setSelected(await storeApi.reject(selected.id, values.reason)); setRejectOpen(false); rejectForm.resetFields(); await load(); message.success('Đã từ chối hồ sơ'); }
    catch (failure) { message.error(failure.message || 'Không từ chối được hồ sơ'); }
    finally { setBusy(false); }
  }

  async function lock(values) {
    setBusy(true);
    try { setSelected(await storeApi.setStoreLocked(selected.id, true, values.reason)); setLockOpen(false); lockForm.resetFields(); await load(); message.success('Đã khóa cửa hàng'); }
    catch (failure) { message.error(failure.message || 'Không khóa được cửa hàng'); }
    finally { setBusy(false); }
  }

  async function unlock() {
    setBusy(true);
    try { setSelected(await storeApi.setStoreLocked(selected.id, false)); await load(); message.success('Đã mở khóa cửa hàng'); }
    catch (failure) { message.error(failure.message || 'Không mở khóa được cửa hàng'); }
    finally { setBusy(false); }
  }

  async function saveCommission() {
    setBusy(true);
    try { setSelected(await storeApi.setCommission(selected.id, Number(selected.commissionRate))); await load(); message.success('Đã cập nhật hoa hồng'); }
    catch (failure) { message.error(failure.message || 'Không cập nhật được hoa hồng'); }
    finally { setBusy(false); }
  }

  function editStore() {
    editForm.setFieldsValue({
      name: selected.name, description: selected.description, phone: selected.phone,
      address: selected.address, latitude: Number(selected.latitude), longitude: Number(selected.longitude),
      openTime: dayjs(`2000-01-01T${selected.openTime}`),
      closeTime: dayjs(`2000-01-01T${selected.closeTime}`),
      bankName: selected.bankName, bankAccountNo: '', bankAccountName: selected.bankAccountName,
    });
    setEditOpen(true);
  }

  async function saveStore(values) {
    const request = { ...values,
      openTime: values.openTime.format('HH:mm'), closeTime: values.closeTime.format('HH:mm'),
      bankAccountNo: values.bankAccountNo?.trim() || null,
    };
    if (request.closeTime <= request.openTime) { message.error('Giờ đóng cửa phải sau giờ mở cửa'); return; }
    setBusy(true);
    try {
      const updated = await storeApi.updateAdminStore(selected.id, request);
      setSelected(updated); setEditOpen(false); await load();
      message.success('Đã cập nhật thông tin cửa hàng');
    } catch (failure) { message.error(failure.message || 'Không cập nhật được cửa hàng'); }
    finally { setBusy(false); }
  }

  return <main className="store-page"><div className="store-page-title"><div><span>QUẢN TRỊ</span><h1>Duyệt cửa hàng</h1><p>Hồ sơ cửa hàng được gửi từ website.</p></div><Select allowClear placeholder="Trạng thái" value={status} style={{ width: 180 }} options={Object.entries(states).map(([value, label]) => ({ value, label }))} onChange={(value) => { setStatus(value); setPage(0); }} /></div>
    {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}
    <Table rowKey="id" loading={loading} dataSource={data.content} pagination={{ current: page + 1, pageSize: 10, total: data.totalElements, showSizeChanger: false, onChange: (current) => setPage(current - 1) }} columns={[
      { title: 'Cửa hàng', dataIndex: 'name' }, { title: 'Chủ tài khoản', dataIndex: 'ownerId', render: (id) => `#${id}` },
      { title: 'Địa chỉ', dataIndex: 'address', ellipsis: true },
      { title: 'Ngày nộp', dataIndex: 'createdAt', render: (date) => date?.replace('T', ' ').slice(0, 16) },
      { title: 'Trạng thái', dataIndex: 'status', render: (value) => <Tag color={value === 'APPROVED' ? 'green' : value === 'PENDING' ? 'gold' : 'red'}>{states[value] || value}</Tag> },
      { title: '', render: (_, row) => <Button icon={<EyeOutlined />} onClick={() => show(row.id)}>Xem hồ sơ</Button> },
    ]} />
    <Modal title="Chi tiết hồ sơ cửa hàng" width={760} open={Boolean(selected)} onCancel={() => { setSelected(null); setLicenseUrl(''); }} footer={<Space><Button onClick={() => setSelected(null)}>Đóng</Button><Button onClick={editStore}>Chỉnh sửa</Button>{selected?.status === 'PENDING' && <><Button danger icon={<CloseOutlined />} onClick={() => setRejectOpen(true)}>Từ chối</Button><Button type="primary" icon={<CheckOutlined />} disabled={!licenseUrl} loading={busy} onClick={() => Modal.confirm({ title: 'Xác nhận duyệt cửa hàng?', content: 'Sau khi duyệt, cửa hàng sẽ hiển thị công khai và Identity sẽ cấp quyền chủ cửa hàng.', okText: 'Duyệt', cancelText: 'Hủy', onOk: approve })}>Duyệt</Button></>}{selected?.status === 'APPROVED' && <Button danger loading={busy} onClick={() => setLockOpen(true)}>Khóa cửa hàng</Button>}{selected?.status === 'LOCKED' && <Button loading={busy} onClick={() => Modal.confirm({ title: 'Mở khóa cửa hàng?', okText: 'Mở khóa', cancelText: 'Hủy', onOk: unlock })}>Mở khóa</Button>}</Space>}>
      {selected && <><Descriptions bordered column={{ xs: 1, sm: 2 }} items={[
        { key: 'name', label: 'Tên cửa hàng', children: selected.name },
        { key: 'owner', label: 'Chủ tài khoản', children: `#${selected.ownerId}` },
        { key: 'phone', label: 'Số điện thoại', children: selected.phone },
        { key: 'hours', label: 'Giờ hoạt động', children: `${selected.openTime?.slice(0, 5)} – ${selected.closeTime?.slice(0, 5)}` },
        { key: 'address', label: 'Địa chỉ', span: 2, children: selected.address },
        { key: 'desc', label: 'Giới thiệu', span: 2, children: selected.description || 'Chưa có' },
        { key: 'coords', label: 'Tọa độ', children: `${selected.latitude}, ${selected.longitude}` },
        { key: 'bank', label: 'Ngân hàng', children: selected.bankName },
        { key: 'account', label: 'Số tài khoản', children: selected.bankAccountNoMasked },
        { key: 'holder', label: 'Chủ tài khoản', children: selected.bankAccountName },
      ]} />{selected.imageUrl && <Image src={selected.imageUrl} alt={selected.name} width={180} style={{ marginTop: 18 }} />}
        <h3>Giấy phép kinh doanh</h3>{licenseUrl ? <Image src={licenseUrl} alt="Giấy phép kinh doanh" width={240} /> : <Alert type="warning" showIcon message="Chưa tải được ảnh giấy phép" />}
        {selected.rejectReason && <Alert style={{ marginTop: 16 }} type="error" showIcon message="Lý do từ chối" description={selected.rejectReason} />}
        {['PENDING', 'APPROVED', 'LOCKED'].includes(selected.status) && <p style={{ marginTop: 16 }}>Hoa hồng: <InputNumber min={0} max={50} value={Number(selected.commissionRate ?? 15)} onChange={(value) => setSelected((old) => ({ ...old, commissionRate: value }))} addonAfter="%" />{selected.status !== 'PENDING' && <Button style={{ marginLeft: 8 }} loading={busy} onClick={() => Modal.confirm({ title: 'Cập nhật hoa hồng?', content: `Cửa hàng ${selected.name} sẽ áp dụng mức ${selected.commissionRate}%.`, okText: 'Cập nhật', cancelText: 'Hủy', onOk: saveCommission })}>Lưu hoa hồng</Button>}</p>}
      </>}
    </Modal>
    <Modal title="Chỉnh sửa cửa hàng" width={760} open={editOpen} onCancel={() => setEditOpen(false)} onOk={() => editForm.submit()} okText="Lưu thay đổi" confirmLoading={busy} destroyOnClose>
      <Form form={editForm} layout="vertical" onFinish={(values) => Modal.confirm({ title: 'Lưu thay đổi cửa hàng?',
        content: 'Thông tin mới sẽ cập nhật hồ sơ cửa hàng.', okText: 'Lưu thay đổi', cancelText: 'Kiểm tra lại', onOk: () => saveStore(values) })}>
        <Row gutter={16}><Col xs={24} sm={12}><Form.Item name="name" label="Tên cửa hàng" rules={[{ required: true, whitespace: true }, { max: 150 }]}><Input maxLength={150} /></Form.Item></Col>
          <Col xs={24} sm={12}><Form.Item name="phone" label="Số điện thoại" rules={[{ required: true, whitespace: true }, { max: 20 }]}><Input maxLength={20} /></Form.Item></Col></Row>
        <Form.Item name="description" label="Giới thiệu" rules={[{ max: 1000 }]}><Input.TextArea rows={3} maxLength={1000} showCount /></Form.Item>
        <Form.Item name="address" label="Địa chỉ" rules={[{ required: true, whitespace: true }, { max: 255 }]}><Input maxLength={255} /></Form.Item>
        <Row gutter={16}><Col xs={12}><Form.Item name="latitude" label="Vĩ độ" rules={[{ required: true }]}><InputNumber min={-90} max={90} precision={8} style={{ width: '100%' }} /></Form.Item></Col>
          <Col xs={12}><Form.Item name="longitude" label="Kinh độ" rules={[{ required: true }]}><InputNumber min={-180} max={180} precision={8} style={{ width: '100%' }} /></Form.Item></Col></Row>
        <Row gutter={16}><Col xs={12}><Form.Item name="openTime" label="Mở cửa" rules={[{ required: true }]}><TimePicker format="HH:mm" style={{ width: '100%' }} /></Form.Item></Col>
          <Col xs={12}><Form.Item name="closeTime" label="Đóng cửa" rules={[{ required: true }]}><TimePicker format="HH:mm" style={{ width: '100%' }} /></Form.Item></Col></Row>
        <Row gutter={16}><Col xs={24} sm={12}><Form.Item name="bankName" label="Ngân hàng" rules={[{ required: true, whitespace: true }, { max: 100 }]}><Input maxLength={100} /></Form.Item></Col>
          <Col xs={24} sm={12}><Form.Item name="bankAccountName" label="Tên chủ tài khoản" rules={[{ required: true, whitespace: true }, { max: 100 }]}><Input maxLength={100} /></Form.Item></Col></Row>
        <Form.Item name="bankAccountNo" label="Số tài khoản mới" extra={`Để trống để giữ số hiện tại (${selected?.bankAccountNoMasked || 'đã lưu'}).`}
          rules={[{ pattern: /^\d{6,30}$/, message: 'Số tài khoản gồm 6–30 chữ số' }]}><Input maxLength={30} /></Form.Item>
      </Form>
    </Modal>
    <Modal title="Lý do từ chối" open={rejectOpen} onOk={() => rejectForm.submit()} okText="Xác nhận từ chối" okButtonProps={{ danger: true, loading: busy }} onCancel={() => setRejectOpen(false)}><Form form={rejectForm} layout="vertical" onFinish={reject}><Form.Item name="reason" label="Lý do" rules={[{ required: true, whitespace: true, message: 'Nhập lý do để chủ cửa hàng biết cần sửa gì' }, { max: 255 }]}><Input.TextArea rows={4} maxLength={255} showCount /></Form.Item></Form></Modal>
    <Modal title="Lý do khóa cửa hàng" open={lockOpen} onOk={() => lockForm.submit()} okText="Xác nhận khóa" okButtonProps={{ danger: true, loading: busy }} onCancel={() => setLockOpen(false)}><Form form={lockForm} layout="vertical" onFinish={lock}><Form.Item name="reason" label="Lý do" rules={[{ required: true, whitespace: true }, { max: 255 }]}><Input.TextArea rows={4} maxLength={255} showCount /></Form.Item></Form></Modal>
  </main>;
}
