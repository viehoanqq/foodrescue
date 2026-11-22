import { useEffect, useState } from 'react';
import { Button, Card, Input, Modal, Popconfirm, Select, Space, Table, Tag, Typography, message } from 'antd';
import { identityApi } from '../../services/identityApi';
import { useAuth } from '../../auth/AuthProvider';

const roles = [
  { value: 'CUSTOMER', label: 'Khách hàng' },
  { value: 'STORE_OWNER', label: 'Chủ cửa hàng' },
  { value: 'STORE_STAFF', label: 'Nhân viên' },
  { value: 'ADMIN', label: 'Quản trị' },
];

export default function AdminUsersPage() {
  const { user: currentUser } = useAuth();
  const [draftKeyword, setDraftKeyword] = useState('');
  const [keyword, setKeyword] = useState('');
  const [role, setRole] = useState();
  const [status, setStatus] = useState();
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [data, setData] = useState({ content: [], totalElements: 0 });
  const [loading, setLoading] = useState(false);
  const [actionId, setActionId] = useState(null);
  const [revision, setRevision] = useState(0);

  useEffect(() => {
    let active = true;
    setLoading(true);
    identityApi.users({ keyword: keyword || undefined, role, status, page, size })
      .then((result) => { if (active) setData(result); })
      .catch((error) => { if (active) message.error(error.message || 'Không tải được tài khoản'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [keyword, role, status, page, size, revision]);

  async function changeStatus(record) {
    setActionId(record.id);
    try {
      const locked = record.status !== 'LOCKED';
      await identityApi.setUserLocked(record.id, locked);
      message.success(locked ? 'Đã khóa tài khoản' : 'Đã mở khóa tài khoản');
      setRevision((value) => value + 1);
    } catch (error) {
      message.error(error.message || 'Không thay đổi được trạng thái');
    } finally {
      setActionId(null);
    }
  }

  async function resetPassword(record) {
    setActionId(record.id);
    try {
      const result = await identityApi.resetPassword(record.id);
      Modal.info({
        title: 'Mật khẩu tạm của ' + record.email,
        content: <Space direction="vertical">
          <Typography.Text>Mật khẩu này chỉ hiển thị một lần. Hãy chuyển cho người dùng qua kênh phù hợp.</Typography.Text>
          <Typography.Text code copyable>{result.temporaryPassword}</Typography.Text>
        </Space>,
      });
      setRevision((value) => value + 1);
    } catch (error) {
      message.error(error.message || 'Không đặt lại được mật khẩu');
    } finally {
      setActionId(null);
    }
  }

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: 'Email', dataIndex: 'email' },
    { title: 'Họ tên', dataIndex: 'fullName' },
    { title: 'Vai trò', dataIndex: 'role', render: (value) => roles.find((item) => item.value === value)?.label || value },
    { title: 'Trạng thái', dataIndex: 'status', render: (value) =>
      <Tag color={value === 'ACTIVE' ? 'green' : 'default'}>{value === 'ACTIVE' ? 'Hoạt động' : 'Đã khóa'}</Tag> },
    { title: 'Thao tác', render: (_, record) => <Space wrap>
      <Popconfirm title={record.status === 'LOCKED' ? 'Mở khóa tài khoản này?' : 'Khóa tài khoản này?'}
        onConfirm={() => changeStatus(record)} okText="Xác nhận" cancelText="Bỏ qua">
        <Button size="small" loading={actionId === record.id} disabled={record.role === 'ADMIN'}>
          {record.status === 'LOCKED' ? 'Mở khóa' : 'Khóa'}
        </Button>
      </Popconfirm>
      <Popconfirm title="Đặt lại mật khẩu cho tài khoản này?" onConfirm={() => resetPassword(record)}
        okText="Xác nhận" cancelText="Bỏ qua">
        <Button size="small" disabled={record.id === currentUser?.id || actionId === record.id}>Đặt lại mật khẩu</Button>
      </Popconfirm>
    </Space> },
  ];

  return <Card style={{ marginTop: 24 }}>
    <Typography.Title level={4}>Quản lý tài khoản</Typography.Title>
    <Space wrap style={{ marginBottom: 16 }}>
      <Input.Search placeholder="Email, họ tên hoặc số điện thoại" allowClear value={draftKeyword}
        onChange={(event) => setDraftKeyword(event.target.value)}
        onSearch={(value) => { setKeyword(value.trim()); setPage(0); }} style={{ width: 280 }} />
      <Select placeholder="Vai trò" allowClear value={role} options={roles} style={{ width: 170 }}
        onChange={(value) => { setRole(value); setPage(0); }} />
      <Select placeholder="Trạng thái" allowClear value={status} style={{ width: 150 }}
        options={[{ value: 'ACTIVE', label: 'Hoạt động' }, { value: 'LOCKED', label: 'Đã khóa' }]}
        onChange={(value) => { setStatus(value); setPage(0); }} />
      <Button onClick={() => setRevision((value) => value + 1)}>Làm mới</Button>
    </Space>
    <Table rowKey="id" loading={loading} dataSource={data.content || []} columns={columns} scroll={{ x: 900 }}
      pagination={{ current: page + 1, pageSize: size, total: data.totalElements || 0,
        showSizeChanger: true, pageSizeOptions: [10, 20, 50, 100],
        onChange: (nextPage, nextSize) => { setPage(nextSize === size ? nextPage - 1 : 0); setSize(nextSize); } }} />
  </Card>;
}
