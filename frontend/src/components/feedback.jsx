import { Modal, message } from 'antd';
import { CheckCircleOutlined, ExclamationCircleOutlined, InfoCircleOutlined } from '@ant-design/icons';

let modalApi = Modal;
let messageApi = message;

export function registerFeedbackApi(nextModal, nextMessage) {
  if (nextModal) modalApi = nextModal;
  if (nextMessage) messageApi = nextMessage;
}

export const toast = {
  success: (content, duration) => messageApi.success(content, duration),
  error: (content, duration) => messageApi.error(content, duration),
  warning: (content, duration) => messageApi.warning(content, duration),
  info: (content, duration) => messageApi.info(content, duration),
};

function badgeFor(tone) {
  if (tone === 'danger') {
    return <span className="fr-modal-badge fr-modal-badge-danger"><ExclamationCircleOutlined /></span>;
  }
  if (tone === 'eco') {
    return <span className="fr-modal-badge fr-modal-badge-eco"><CheckCircleOutlined /></span>;
  }
  if (tone === 'info') {
    return <span className="fr-modal-badge fr-modal-badge-eco"><InfoCircleOutlined /></span>;
  }
  return <span className="fr-modal-badge fr-modal-badge-warning"><ExclamationCircleOutlined /></span>;
}

export function confirmDialog({
  title,
  content,
  okText = 'Xác nhận',
  cancelText = 'Quay lại',
  danger = false,
  tone = danger ? 'danger' : 'warning',
  width = 460,
  onOk,
  onCancel,
}) {
  return modalApi.confirm({
    title,
    content,
    icon: badgeFor(tone),
    width,
    centered: true,
    className: 'fr-feedback-modal',
    okText,
    cancelText,
    okButtonProps: danger ? { danger: true } : undefined,
    onOk,
    onCancel,
  });
}

export function infoDialog({ title, content, okText = 'Đã hiểu', width = 460, onOk }) {
  return modalApi.info({
    title,
    content,
    icon: badgeFor('info'),
    width,
    centered: true,
    className: 'fr-feedback-modal',
    okText,
    onOk,
  });
}
