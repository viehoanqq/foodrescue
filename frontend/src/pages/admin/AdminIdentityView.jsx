import { useEffect, useState } from 'react';
import { Button, Card, Input, Space, Table, Tabs, Typography, message, Modal } from 'antd';
import { identityApi } from '../../services/identityApi';

export default function AdminIdentityView() {
  const [users, setUsers] = useState([]);
  const [logs, setLogs] = useState([]);
  const [keyword, setKeyword] = useState('');
  const [busy, setBusy] = useState(false);

  async function load() {
    setBusy(true);
    try {
      const [userPage, logPage] = await Promise.all([
        identityApi.users({ keyword, size: 100 }),
        identityApi.auditLogs({ size: 50 }),
      ]);
      setUsers(userPage.content);
      setLogs(logPage.content);
    } catch (error) {
      message.error(error.message || 'Không tải được dữ liệu');
    } finally {
      setBusy(false);
    }
  }

  useEffect(() => { load(); }, []);

  async function changeStatus(id, status) {
    try {
      await identityApi.setUserLocked(id, status !== 'LOCKED');
      await load();
    } catch (error) { message.error(error.message); }
  }

  async function resetPassword(id) {
    try {
      const result = await identityApi.resetPassword(id);
      Modal.info({ title: 'Mật khẩu tạm', content: result.temporaryPassword });
      await load();
    } catch (error) { message.error(error.message); }
  }

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: 'Email', dataIndex: 'email' },
    { title: 'Họ tên', dataIndex: 'fullName' },
    { title: 'Vai trò', dataIndex: 'role' },
    { title: 'Trạng thái', dataIndex: 'status' },
    { title: 'Thao tác', render: (_, row) => <Space>
      <Button size="small" disabled={row.role === 'ADMIN'} onClick={() => changeStatus(row.id, row.status)}>
        {row.status === 'LOCKED' ? 'Mở khóa' : 'Khóa'}
      </Button>
      <Button size="small" onClick={() => resetPassword(row.id)}>Đặt lại mật khẩu</Button>
    </Space> },
  ];

  return <Card>
    <Typography.Title level={4}>Quản lý tài khoản</Typography.Title>
    <Tabs items={[
      { key: 'users', label: 'Người dùng', children: <>
        <Space style={{ marginBottom: 16 }}>
          <Input.Search value={keyword} onChange={e => setKeyword(e.target.value)} onSearch={load}
            placeholder="Tìm email, tên, số điện thoại" allowClear />
          <Button onClick={load}>Làm mới</Button>
        </Space>
        <Table rowKey="id" loading={busy} dataSource={users} columns={columns} pagination={{ pageSize: 20 }} />
      </> },
      { key: 'audit', label: 'Nhật ký', children: <Table rowKey="id" loading={busy} dataSource={logs}
        columns={[
          { title: 'Thời gian', dataIndex: 'createdAt' },
          { title: 'Người thao tác', dataIndex: 'actorEmail', render: value => value || 'Hệ thống' },
          { title: 'Hành động', dataIndex: 'action' },
          { title: 'Đối tượng', render: (_, row) => `${row.entityType} #${row.entityId}` },
        ]} pagination={{ pageSize: 20 }} /> },
    ]} />
  </Card>;
}
