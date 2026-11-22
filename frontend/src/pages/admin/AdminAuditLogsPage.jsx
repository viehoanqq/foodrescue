import { useEffect, useState } from 'react';
import { Button, Card, DatePicker, Form, Input, InputNumber, Space, Table, Typography, message } from 'antd';
import { identityApi } from '../../services/identityApi';

const { RangePicker } = DatePicker;

export default function AdminAuditLogsPage() {
  const [form] = Form.useForm();
  const [filters, setFilters] = useState({});
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [data, setData] = useState({ content: [], totalElements: 0 });
  const [loading, setLoading] = useState(false);
  const [revision, setRevision] = useState(0);

  useEffect(() => {
    let active = true;
    setLoading(true);
    identityApi.auditLogs({ ...filters, page, size })
      .then((result) => { if (active) setData(result); })
      .catch((error) => { if (active) message.error(error.message || 'Không tải được nhật ký'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [filters, page, size, revision]);

  function search(values) {
    setFilters({
      actorId: values.actorId,
      action: values.action?.trim() || undefined,
      entityType: values.entityType?.trim() || undefined,
      entityId: values.entityId,
      from: values.range?.[0]?.startOf('day').toISOString(),
      to: values.range?.[1]?.endOf('day').toISOString(),
    });
    setPage(0);
  }

  const columns = [
    { title: 'Thời gian', dataIndex: 'createdAt', width: 165,
      render: (value) => value ? String(value).replace('T', ' ').slice(0, 19) : '—' },
    { title: 'Người thao tác', dataIndex: 'actorEmail', render: (value) => value || 'Hệ thống' },
    { title: 'Hành động', dataIndex: 'action' },
    { title: 'Đối tượng', render: (_, record) => `${record.entityType} #${record.entityId}` },
    { title: 'Số tiền', dataIndex: 'amount', render: (value) => value == null ? '—' : `${Number(value).toLocaleString('vi-VN')} đ` },
    { title: 'Chi tiết', dataIndex: 'detail', render: (value) => value || '—' },
  ];

  return <Card style={{ marginTop: 24 }}>
    <Typography.Title level={4}>Nhật ký hệ thống</Typography.Title>
    <Form form={form} layout="inline" onFinish={search} style={{ gap: 8, marginBottom: 16 }}>
      <Form.Item name="actorId"><InputNumber placeholder="ID người thao tác" min={1} style={{ width: 160 }} /></Form.Item>
      <Form.Item name="action"><Input placeholder="Hành động" style={{ width: 150 }} /></Form.Item>
      <Form.Item name="entityType"><Input placeholder="Loại đối tượng" style={{ width: 150 }} /></Form.Item>
      <Form.Item name="entityId"><InputNumber placeholder="ID đối tượng" min={1} style={{ width: 140 }} /></Form.Item>
      <Form.Item name="range"><RangePicker format="DD/MM/YYYY" placeholder={['Từ ngày', 'Đến ngày']} /></Form.Item>
      <Form.Item><Space>
        <Button type="primary" htmlType="submit">Lọc</Button>
        <Button onClick={() => { form.resetFields(); setFilters({}); setPage(0); }}>Xóa lọc</Button>
        <Button onClick={() => setRevision((value) => value + 1)}>Làm mới</Button>
      </Space></Form.Item>
    </Form>
    <Table rowKey="id" loading={loading} dataSource={data.content || []} columns={columns} scroll={{ x: 1000 }}
      pagination={{ current: page + 1, pageSize: size, total: data.totalElements || 0,
        showSizeChanger: true, pageSizeOptions: [10, 20, 50, 100],
        onChange: (nextPage, nextSize) => { setPage(nextSize === size ? nextPage - 1 : 0); setSize(nextSize); } }} />
  </Card>;
}
