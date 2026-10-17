import { App, Button, Popconfirm, Space, Tooltip } from 'antd';
import { BookOutlined, EditOutlined, StopOutlined, SwapOutlined } from '@ant-design/icons';

const STATIC_NOTE = 'Giao diện tĩnh, chưa nối API';

/**
 * Các nút thao tác trên một túi (giao diện tĩnh S0).
 * Quyền thật (chủ cửa hàng / nhân viên) sẽ do việc S5, S6, S8 xử lý:
 *  - Sửa, Huỷ: chỉ STORE_OWNER
 *  - Thêm/bớt, Sổ kho: STORE_OWNER và STORE_STAFF
 */
export default function BatchActions({ batch, onEdit }) {
  const { message } = App.useApp();
  const live = batch.status === 'AVAILABLE' || batch.status === 'SOLD_OUT';

  return (
    <Space size={4} wrap>
      <Tooltip title="Sửa túi">
        <Button type="text" icon={<EditOutlined />} disabled={!live} aria-label="Sửa túi" onClick={() => onEdit?.(batch)} />
      </Tooltip>
      <Tooltip title="Thêm / bớt số lượng">
        <Button
          type="text"
          icon={<SwapOutlined />}
          disabled={!live}
          aria-label="Thêm hoặc bớt số lượng"
          onClick={() => message.info(STATIC_NOTE)}
        />
      </Tooltip>
      <Tooltip title="Sổ kho">
        <Button type="text" icon={<BookOutlined />} aria-label="Xem sổ kho" onClick={() => message.info(STATIC_NOTE)} />
      </Tooltip>
      <Popconfirm
        title="Huỷ túi này?"
        description="Các đơn đã thanh toán sẽ được hoàn tiền toàn bộ."
        okText="Huỷ túi"
        cancelText="Không"
        okButtonProps={{ danger: true }}
        disabled={!live}
        onConfirm={() => message.info(STATIC_NOTE)}
      >
        <Tooltip title="Huỷ túi">
          <Button type="text" danger icon={<StopOutlined />} disabled={!live} aria-label="Huỷ túi" />
        </Tooltip>
      </Popconfirm>
    </Space>
  );
}
