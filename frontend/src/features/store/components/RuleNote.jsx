import { SafetyCertificateFilled } from '@ant-design/icons';
import { S } from '../styles/stitchTheme';

/** Ô nhắc quy tắc nghiệp vụ (kiểu hộp "Quy tắc an ninh BR-01" trong thiết kế Stitch). */
export default function RuleNote({ code, tag = 'Bắt buộc', children }) {
  return (
    <div
      style={{
        display: 'flex',
        gap: 12,
        padding: '12px 16px',
        borderRadius: 14,
        background: S.infoBg,
        border: `1px solid ${S.softBorder}`,
      }}
    >
      <SafetyCertificateFilled style={{ color: S.secondary, fontSize: 20, marginTop: 2 }} />
      <div>
        <div style={{ fontWeight: 700, color: S.navy }}>
          Quy tắc {code}
          <span
            style={{
              marginLeft: 8,
              padding: '1px 8px',
              borderRadius: 6,
              background: S.secondarySoft,
              color: S.secondaryText,
              fontSize: 11,
              fontWeight: 700,
            }}
          >
            {tag}
          </span>
        </div>
        <div style={{ color: S.textMuted, fontSize: 13, lineHeight: 1.5 }}>{children}</div>
      </div>
    </div>
  );
}
