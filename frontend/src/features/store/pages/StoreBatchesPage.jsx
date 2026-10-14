import { useMemo, useState } from 'react';
import { Button, Empty, Grid, Segmented, Table, Tag, Typography } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { BATCH_STATUS } from '../../../shared/constants/status';
import { discountPercent, formatMoney, formatPickupWindow } from '../../../shared/utils/format';
import BatchActions from '../components/BatchActions';
import BatchFormModal from '../components/BatchFormModal';
import RuleNote from '../components/RuleNote';
import StoreShell from '../components/StoreShell';
import { MOCK_BATCHES } from '../mockData';
import { S } from '../styles/stitchTheme';

const FILTERS = [
  { value: 'ALL', label: 'Tất cả' },
  { value: 'AVAILABLE', label: BATCH_STATUS.AVAILABLE.label },
  { value: 'SOLD_OUT', label: BATCH_STATUS.SOLD_OUT.label },
  { value: 'EXPIRED', label: BATCH_STATUS.EXPIRED.label },
  { value: 'CANCELLED', label: BATCH_STATUS.CANCELLED.label },
];

function StatusBadge({ status }) {
  const s = BATCH_STATUS[status];
  return <Tag color={s?.color}>{s?.label ?? status}</Tag>;
}

function PriceCell({ batch }) {
  return (
    <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 8 }}>
      <span style={{ color: S.primary, fontWeight: 800, fontSize: 16 }}>{formatMoney(batch.rescuePrice)}</span>
      <span style={{ color: S.textMuted, textDecoration: 'line-through', fontSize: 13 }}>{formatMoney(batch.originalPrice)}</span>
      <span style={{ padding: '1px 8px', borderRadius: 8, background: S.secondarySoft, color: S.secondaryText, fontSize: 12, fontWeight: 700 }}>
        -{discountPercent(batch.originalPrice, batch.rescuePrice)}%
      </span>
    </div>
  );
}

function StatTile({ value, label }) {
  return (
    <div style={{ padding: '14px 18px', borderRadius: 16, background: S.soft, border: `1px solid ${S.softBorder}` }}>
      <div style={{ fontSize: 28, fontWeight: 800, color: S.primary, lineHeight: 1.1 }}>{value}</div>
      <div style={{ fontSize: 13, fontWeight: 600, color: S.textMuted, marginTop: 2 }}>{label}</div>
    </div>
  );
}

/** Quản lý túi giải cứu của cửa hàng: /store/batches (giao diện tĩnh S0, dữ liệu giả). */
export default function StoreBatchesPage() {
  const screens = Grid.useBreakpoint();
  const isDesktop = Boolean(screens.md);
  const [filter, setFilter] = useState('ALL');
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState(null);

  const rows = useMemo(() => (filter === 'ALL' ? MOCK_BATCHES : MOCK_BATCHES.filter((b) => b.status === filter)), [filter]);

  // BR-18: đã bán = quantity - remaining_quantity
  const stats = useMemo(() => {
    const live = MOCK_BATCHES.filter((b) => b.status === 'AVAILABLE');
    return {
      selling: live.length,
      remaining: live.reduce((sum, b) => sum + b.remainingQuantity, 0),
      sold: MOCK_BATCHES.reduce((sum, b) => sum + (b.quantity - b.remainingQuantity), 0),
    };
  }, []);

  const openCreate = () => {
    setEditing(null);
    setFormOpen(true);
  };
  const openEdit = (batch) => {
    setEditing(batch);
    setFormOpen(true);
  };

  const columns = [
    {
      title: 'Túi',
      dataIndex: 'title',
      render: (_, b) => (
        <div>
          <div style={{ fontWeight: 700, color: S.navy }}>{b.title}</div>
          <div style={{ color: S.textMuted, fontSize: 13 }}>{b.categoryName}</div>
        </div>
      ),
    },
    { title: 'Giá', render: (_, b) => <PriceCell batch={b} /> },
    { title: 'Còn lại', align: 'center', render: (_, b) => `${b.remainingQuantity} / ${b.quantity}` },
    { title: 'Giờ lấy hàng', render: (_, b) => formatPickupWindow(b.pickupStart, b.pickupEnd) },
    { title: 'Trạng thái', render: (_, b) => <StatusBadge status={b.status} /> },
    { title: 'Thao tác', width: 168, render: (_, b) => <BatchActions batch={b} onEdit={openEdit} /> },
  ];

  return (
    <StoreShell rule="BR-23">
      <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'flex-end', justifyContent: 'space-between', gap: 16 }}>
        <div>
          <div style={{ color: S.primary, fontWeight: 800, fontSize: 13, letterSpacing: 0.4 }}>PHÂN HỆ CỬA HÀNG</div>
          <Typography.Title level={2} style={{ margin: '4px 0 4px', color: S.navy, fontWeight: 800 }}>
            Túi giải cứu
          </Typography.Title>
          <Typography.Text style={{ color: S.textMuted }}>Đăng túi, theo dõi số lượng còn lại và giờ lấy hàng.</Typography.Text>
        </div>
        <Button type="primary" icon={<PlusOutlined />} size="large" onClick={openCreate}>
          Đăng túi mới
        </Button>
      </div>

      <div className="fr-stats" style={{ marginTop: 20 }}>
        <StatTile value={stats.selling} label="Túi đang bán" />
        <StatTile value={stats.remaining} label="Suất còn lại" />
        <StatTile value={stats.sold} label="Suất đã bán" />
      </div>

      <div style={{ marginTop: 16 }}>
        <RuleNote code="BR-23" tag="Sổ kho">
          Số lượng túi chỉ thay đổi qua thao tác Thêm / bớt, mỗi lần được ghi một dòng vào sổ kho.
        </RuleNote>
      </div>

      <div style={{ margin: '20px 0 16px', overflowX: 'auto' }}>
        <Segmented options={FILTERS} value={filter} onChange={setFilter} />
      </div>

      {rows.length === 0 ? (
        <div style={{ padding: 32, borderRadius: S.radiusCard, background: S.surface, boxShadow: S.shadowCard }}>
          <Empty description="Chưa có túi nào ở trạng thái này" />
        </div>
      ) : isDesktop ? (
        <div style={{ borderRadius: S.radiusCard, background: S.surface, boxShadow: S.shadowCard, overflow: 'hidden' }}>
          <Table rowKey="id" columns={columns} dataSource={rows} pagination={false} />
        </div>
      ) : (
        <div style={{ display: 'grid', gap: 12 }}>
          {rows.map((b) => (
            <div key={b.id} style={{ padding: 16, borderRadius: 16, background: S.surface, border: `1px solid ${S.softBorder}`, boxShadow: S.shadowCard }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8 }}>
                <div>
                  <div style={{ fontWeight: 700, color: S.navy }}>{b.title}</div>
                  <div style={{ color: S.textMuted, fontSize: 13 }}>{b.categoryName}</div>
                </div>
                <StatusBadge status={b.status} />
              </div>
              <div style={{ margin: '10px 0' }}>
                <PriceCell batch={b} />
              </div>
              <div style={{ color: S.textMuted, fontSize: 13 }}>
                Còn {b.remainingQuantity} / {b.quantity} túi, lấy {formatPickupWindow(b.pickupStart, b.pickupEnd)}
              </div>
              <div style={{ marginTop: 8, marginLeft: -8 }}>
                <BatchActions batch={b} onEdit={openEdit} />
              </div>
            </div>
          ))}
        </div>
      )}

      <BatchFormModal open={formOpen} batch={editing} onClose={() => setFormOpen(false)} />
    </StoreShell>
  );
}
