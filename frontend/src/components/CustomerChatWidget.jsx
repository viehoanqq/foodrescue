import { useEffect, useRef, useState } from 'react';
import { Button, Input, Spin } from 'antd';
import { CloseOutlined, DeleteOutlined, MessageOutlined, RobotOutlined, SendOutlined, ShopOutlined, ShoppingOutlined } from '@ant-design/icons';
import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthProvider';
import { chatApi } from '../services/chatApi';
import './customerChatWidget.css';

const GUEST_PROMPTS = [
  'Có những cửa hàng nào đang mở?',
  'Tìm cửa hàng bánh mì gần tôi',
  'Trợ lý có thể giúp gì cho tôi?',
];

const CUSTOMER_PROMPTS = [
  'Tháng này tôi tiêu hết bao nhiêu tiền, có mấy đơn?',
  'Kiểm tra giỏ hàng của tôi',
  'Tìm cửa hàng gần tôi',
];

function formatReplyLines(text) {
  return String(text || '').split('\n').map((line, lineIdx) => {
    const parts = line.split(/(\*\*[^*]+\*\*)/g);
    return (
      <span key={lineIdx} className="chat-line">
        {parts.map((part, partIdx) => (
          part.startsWith('**') && part.endsWith('**') && part.length > 4
            ? <strong key={partIdx}>{part.slice(2, -2)}</strong>
            : part
        ))}
      </span>
    );
  });
}

export default function CustomerChatWidget() {
  const { user } = useAuth();
  const [open, setOpen] = useState(false);
  const [input, setInput] = useState('');
  const [sending, setSending] = useState(false);
  const [messages, setMessages] = useState([]);
  const listRef = useRef(null);

  useEffect(() => {
    if (open && listRef.current) {
      listRef.current.scrollTop = listRef.current.scrollHeight;
    }
  }, [open, messages, sending]);

  if (user && user.role !== 'CUSTOMER') {
    return null;
  }

  const quickPrompts = user?.role === 'CUSTOMER' ? CUSTOMER_PROMPTS : GUEST_PROMPTS;

  async function sendText(rawText) {
    const text = (rawText ?? input).trim();
    if (!text || sending) return;

    const history = messages
      .filter((item) => !item.error)
      .slice(-10)
      .map((item) => ({ role: item.role, text: item.text }));

    setInput('');
    setMessages((prev) => [...prev, { id: Date.now(), role: 'user', text }]);
    setSending(true);

    try {
      const response = await chatApi.send({
        message: text,
        history,
        latitude: user?.latitude != null ? Number(user.latitude) : undefined,
        longitude: user?.longitude != null ? Number(user.longitude) : undefined,
      });
      setMessages((prev) => [
        ...prev,
        {
          id: Date.now() + 1,
          role: 'model',
          text: response.reply,
          toolsUsed: response.toolsUsed || [],
          suggestions: response.suggestions || [],
        },
      ]);
    } catch (error) {
      setMessages((prev) => [
        ...prev,
        {
          id: Date.now() + 1,
          role: 'model',
          text: error.message || 'Không kết nối được trợ lý AI. Hãy kiểm tra api-gateway đã chạy chưa.',
          error: true,
        },
      ]);
    } finally {
      setSending(false);
    }
  }

  function handleKeyDown(event) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      sendText();
    }
  }

  return (
    <div className="fr-chat-root">
      {open && (
        <section className="fr-chat-panel" aria-label="Trợ lý AI FoodRescue">
          <header className="fr-chat-header">
            <div className="fr-chat-title">
              <span className="fr-chat-avatar"><RobotOutlined /></span>
              <div>
                <strong>Trợ lý FoodRescue</strong>
                <small>{user ? `Xin chào ${user.fullName || user.email}` : 'Hỏi đáp quán ăn & đơn hàng'}</small>
              </div>
            </div>
            <div className="fr-chat-header-actions">
              {messages.length > 0 && (
                <Button
                  type="text"
                  size="small"
                  icon={<DeleteOutlined />}
                  aria-label="Xóa hội thoại"
                  onClick={() => setMessages([])}
                />
              )}
              <Button
                type="text"
                size="small"
                icon={<CloseOutlined />}
                aria-label="Đóng chat"
                onClick={() => setOpen(false)}
              />
            </div>
          </header>

          <div className="fr-chat-messages" ref={listRef}>
            {messages.length === 0 ? (
              <div className="fr-chat-welcome">
                <p>Mình có thể tìm cửa hàng đang mở, xem đánh giá quán, kiểm tra giỏ hàng hoặc thống kê đơn &amp; chi tiêu của bạn.</p>
                <div className="fr-chat-prompts">
                  {quickPrompts.map((prompt) => (
                    <button
                      type="button"
                      key={prompt}
                      className="fr-chat-prompt-btn"
                      onClick={() => sendText(prompt)}
                    >
                      {prompt}
                    </button>
                  ))}
                </div>
              </div>
            ) : (
              messages.map((item) => (
                <div key={item.id} className={`fr-chat-msg fr-chat-msg-${item.role}`}>
                  <div className={`fr-chat-bubble ${item.error ? 'fr-chat-bubble-error' : ''}`}>
                    {formatReplyLines(item.text)}
                  </div>
                  {item.toolsUsed?.length > 0 && (
                    <div className="fr-chat-tools">
                      Đã tra cứu: {item.toolsUsed.join(', ')}
                    </div>
                  )}
                  {item.suggestions?.length > 0 && (
                    <div className="fr-chat-suggestions">
                      {item.suggestions.map((suggestion) => (
                        <Link
                          key={`${suggestion.type}-${suggestion.id}`}
                          to={suggestion.path}
                          className="fr-chat-suggestion"
                          onClick={() => setOpen(false)}
                        >
                          {suggestion.type === 'STORE' ? <ShopOutlined /> : <ShoppingOutlined />}
                          <div>
                            <strong>{suggestion.title}</strong>
                            {suggestion.subtitle && <span>{suggestion.subtitle}</span>}
                          </div>
                        </Link>
                      ))}
                    </div>
                  )}
                </div>
              ))
            )}
            {sending && (
              <div className="fr-chat-msg fr-chat-msg-model">
                <div className="fr-chat-bubble fr-chat-loading">
                  <Spin size="small" /> <span>Đang phân tích dữ liệu...</span>
                </div>
              </div>
            )}
          </div>

          <footer className="fr-chat-composer">
            <Input.TextArea
              value={input}
              onChange={(event) => setInput(event.target.value)}
              onKeyDown={handleKeyDown}
              placeholder="Nhập câu hỏi của bạn..."
              autoSize={{ minRows: 1, maxRows: 3 }}
              maxLength={1000}
              disabled={sending}
            />
            <Button
              type="primary"
              icon={<SendOutlined />}
              loading={sending}
              disabled={!input.trim()}
              onClick={() => sendText()}
              aria-label="Gửi tin nhắn"
            />
          </footer>
        </section>
      )}

      <button
        type="button"
        className="fr-chat-launcher"
        onClick={() => setOpen((prev) => !prev)}
        aria-label={open ? 'Đóng trợ lý AI' : 'Mở trợ lý AI'}
      >
        {open ? <CloseOutlined /> : <MessageOutlined />}
        <span>{open ? 'Thu gọn' : 'Trợ lý AI'}</span>
      </button>
    </div>
  );
}
