import { useEffect } from 'react';
import { App as AntApp } from 'antd';
import { registerFeedbackApi } from './feedback';

export default function FeedbackBridge() {
  const { modal, message } = AntApp.useApp();
  useEffect(() => {
    registerFeedbackApi(modal, message);
  }, [modal, message]);
  return null;
}
