import { ConfigProvider } from 'antd';
import { SafetyCertificateOutlined } from '@ant-design/icons';
import { S, stitchTheme } from '../styles/stitchTheme';
import '../styles/store.css';

function LeafLogo() {
  return (
    <div
      aria-hidden="true"
      style={{ width: 44, height: 44, borderRadius: 12, background: S.primary, display: 'grid', placeItems: 'center' }}
    >
      <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="#fff" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
        <path d="M5 19c0-8 5-13 14-14 0 9-5 14-13 14z" />
        <path d="M5 19c3-3 6-6 9-8" />
      </svg>
    </div>
  );
}

/**
 * Khung chung của các trang module cửa hàng: áp theme Stitch + thanh thương hiệu.
 * `rule`: mã quy tắc nghiệp vụ hiển thị ở góc phải thanh thương hiệu (vd "BR-02").
 */
export default function StoreShell({ children, maxWidth = 1200, rule }) {
  return (
    <ConfigProvider theme={stitchTheme} variant="filled">
      <div className="fr-store">
        <div style={{ maxWidth, margin: '0 auto', padding: '20px 16px 48px' }}>
          <header className="fr-brandbar">
            <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              <LeafLogo />
              <div>
                <div style={{ fontSize: 22, fontWeight: 800, color: S.navy, lineHeight: 1.1 }}>FoodRescue</div>
                <div style={{ fontSize: 13, color: S.textMuted }}>Giải cứu thực phẩm cuối ngày</div>
              </div>
            </div>
            {rule && (
              <div
                className="fr-brandbar__chip"
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: 8,
                  padding: '8px 16px',
                  borderRadius: 999,
                  background: S.secondarySoft,
                  color: S.secondaryText,
                  fontSize: 13,
                  fontWeight: 700,
                }}
              >
                <SafetyCertificateOutlined />
                Quy tắc nghiệp vụ: {rule}
              </div>
            )}
          </header>
          {children}
        </div>
      </div>
    </ConfigProvider>
  );
}
