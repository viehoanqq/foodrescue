import { App, Col, DatePicker, Form, Input, InputNumber, Modal, Row, Select, Typography, Upload } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import RuleNote from './RuleNote';
import { S } from '../styles/stitchTheme';
import { discountPercent, formatMoney } from '../../../shared/utils/format';
import { MOCK_CATEGORIES } from '../mockData';

const MAX_RESCUE_RATIO = 0.6; // BR-03: giá giải cứu <= 60% giá gốc (giảm tối thiểu 40%)
const moneyInput = {
  min: 1000,
  step: 1000,
  style: { width: '100%' },
  addonAfter: 'đ',
  formatter: (v) => `${v}`.replace(/\B(?=(\d{3})+(?!\d))/g, '.'),
  parser: (v) => Number(`${v}`.replace(/\./g, '')),
};

/** Form đăng / sửa túi giải cứu (giao diện tĩnh S0; có sẵn kiểm tra giá và khung giờ như BR-03, BR-04). */
export default function BatchFormModal({ open, batch, onClose }) {
  const { message } = App.useApp();
  const [form] = Form.useForm();
  const editing = Boolean(batch);

  const handleFinish = () => {
    message.info('Giao diện tĩnh, chưa nối API (việc S5)');
    onClose();
  };

  return (
    <Modal
      title={editing ? 'Sửa túi giải cứu' : 'Đăng túi giải cứu'}
      open={open}
      onCancel={onClose}
      afterClose={() => form.resetFields()}
      okText={editing ? 'Lưu thay đổi' : 'Đăng túi'}
      cancelText="Đóng"
      onOk={() => form.submit()}
      width="min(560px, calc(100vw - 32px))"
      forceRender
    >
      <Form
        form={form}
        layout="vertical"
        requiredMark={false}
        onFinish={handleFinish}
        size="large"
        initialValues={batch ? { title: batch.title, originalPrice: batch.originalPrice, rescuePrice: batch.rescuePrice, quantity: batch.quantity } : undefined}
      >
        <div style={{ marginBottom: 16 }}>
          <RuleNote code="BR-03, BR-04">
            Giá giải cứu tối đa 60% giá gốc (giảm ít nhất 40%). Giờ kết thúc phải sau giờ bắt đầu và chưa trôi qua.
          </RuleNote>
        </div>

        <Form.Item name="title" label="Tên túi" rules={[{ required: true, message: 'Nhập tên túi' }]}>
          <Input maxLength={150} placeholder="Ví dụ: Túi Rau Củ Cuối Ngày" />
        </Form.Item>

        <Form.Item name="categoryId" label="Danh mục" rules={[{ required: true, message: 'Chọn danh mục' }]}>
          <Select placeholder="Chọn danh mục" options={MOCK_CATEGORIES.map((c) => ({ value: c.id, label: c.name }))} />
        </Form.Item>

        <Form.Item name="description" label="Mô tả">
          <Input.TextArea rows={3} maxLength={500} showCount placeholder="Trong túi có gì, lưu ý khi nhận hàng..." />
        </Form.Item>

        <Row gutter={12}>
          <Col xs={24} sm={12}>
            <Form.Item name="originalPrice" label="Giá gốc" rules={[{ required: true, message: 'Nhập giá gốc' }]}>
              <InputNumber {...moneyInput} />
            </Form.Item>
          </Col>
          <Col xs={24} sm={12}>
            <Form.Item
              name="rescuePrice"
              label="Giá giải cứu"
              dependencies={['originalPrice']}
              rules={[
                { required: true, message: 'Nhập giá giải cứu' },
                ({ getFieldValue }) => ({
                  validator(_, value) {
                    const original = getFieldValue('originalPrice');
                    if (!value || !original || value <= original * MAX_RESCUE_RATIO) return Promise.resolve();
                    return Promise.reject(new Error('Giá giải cứu phải giảm ít nhất 40%'));
                  },
                }),
              ]}
            >
              <InputNumber {...moneyInput} />
            </Form.Item>
          </Col>
        </Row>

        <Form.Item
          noStyle
          shouldUpdate={(prev, cur) => prev.originalPrice !== cur.originalPrice || prev.rescuePrice !== cur.rescuePrice}
        >
          {({ getFieldValue }) => {
            const original = getFieldValue('originalPrice');
            const rescue = getFieldValue('rescuePrice');
            if (!original) return null;
            const percent = rescue ? discountPercent(original, rescue) : null;
            return (
              <Typography.Paragraph style={{ marginTop: -8, fontSize: 13, color: S.textMuted }}>
                Giá giải cứu tối đa {formatMoney(Math.floor(original * MAX_RESCUE_RATIO))}.
                {percent !== null && (
                  <span style={{ marginLeft: 8, color: percent >= 40 ? S.primary : S.danger, fontWeight: 700 }}>
                    Giảm {percent}%
                  </span>
                )}
              </Typography.Paragraph>
            );
          }}
        </Form.Item>

        <Form.Item name="quantity" label="Số lượng túi" rules={[{ required: true, message: 'Nhập số lượng' }]}>
          <InputNumber min={1} max={999} style={{ width: '100%' }} />
        </Form.Item>

        <Form.Item
          name="pickupRange"
          label="Khung giờ lấy hàng"
          rules={[
            { required: true, message: 'Chọn khung giờ lấy hàng' },
            {
              validator(_, range) {
                if (!range) return Promise.resolve();
                const [start, end] = range;
                if (!end.isAfter(start) || !end.isAfter(dayjs())) {
                  return Promise.reject(new Error('Giờ kết thúc phải sau giờ bắt đầu và chưa trôi qua'));
                }
                return Promise.resolve();
              },
            },
          ]}
        >
          <DatePicker.RangePicker showTime={{ format: 'HH:mm' }} format="DD/MM/YYYY HH:mm" style={{ width: '100%' }} />
        </Form.Item>

        <Form.Item name="image" label="Ảnh túi" valuePropName="fileList" getValueFromEvent={(e) => e?.fileList}>
          <Upload listType="picture-card" maxCount={1} accept="image/*" beforeUpload={() => false}>
            <div>
              <PlusOutlined />
              <div style={{ marginTop: 4 }}>Tải ảnh</div>
            </div>
          </Upload>
        </Form.Item>
      </Form>
    </Modal>
  );
}
