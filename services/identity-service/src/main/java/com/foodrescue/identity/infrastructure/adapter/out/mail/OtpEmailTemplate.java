package com.foodrescue.identity.infrastructure.adapter.out.mail;

import com.foodrescue.identity.domain.model.enums.OtpType;

/** Builds email-client-safe HTML (inline CSS + table layout) and plain-text fallback for OTP emails. */
public final class OtpEmailTemplate {
    private OtpEmailTemplate() {}

    public static EmailContent build(OtpType type, String code) {
        return switch (type) {
            case REGISTRATION -> template(
                    "FoodRescue - Mã xác minh đăng ký",
                    "XÁC MINH ĐĂNG KÝ",
                    "#1E8E4E",
                    "#E8F5EE",
                    "Hoàn tất đăng ký tài khoản",
                    "Cảm ơn bạn đã tham gia <strong>FoodRescue</strong>. Hãy nhập mã xác minh bên dưới để kích hoạt tài khoản của bạn:",
                    "Nếu bạn không thực hiện đăng ký trên FoodRescue, vui lòng bỏ qua email này.",
                    "Mã xác minh FoodRescue của bạn là: " + code
                            + "\nMã có hiệu lực trong 10 phút. Không chia sẻ mã này với người khác.",
                    code);
            case PASSWORD_RESET -> template(
                    "FoodRescue - Mã xác minh đặt lại mật khẩu",
                    "ĐẶT LẠI MẬT KHẨU",
                    "#D95800",
                    "#FFF1E6",
                    "Yêu cầu đặt lại mật khẩu",
                    "Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản <strong>FoodRescue</strong> của bạn. Nhập mã OTP bên dưới để xác nhận đổi mật khẩu:",
                    "Nếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua email này — mật khẩu hiện tại của bạn sẽ không thay đổi.",
                    "Mã xác minh đặt lại mật khẩu FoodRescue của bạn là: " + code
                            + "\nMã có hiệu lực trong 10 phút. Không chia sẻ mã này với người khác.",
                    code);
        };
    }

    private static EmailContent template(String subject, String badgeLabel, String badgeColor, String badgeBg,
                                         String heading, String introHtml, String ignoreNotice,
                                         String plainText, String code) {
        String preheader = "Mã OTP FoodRescue của bạn là " + code + " (hiệu lực trong 10 phút).";
        String html = """
                <!DOCTYPE html>
                <html lang="vi">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1.0">
                  <title>%s</title>
                </head>
                <body style="margin:0;padding:0;background-color:#f3faf5;font-family:'Be Vietnam Pro','Segoe UI',Roboto,Arial,sans-serif;color:#1F1F1F;">
                  <div style="display:none;max-height:0;overflow:hidden;opacity:0;color:transparent;">%s</div>
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="background-color:#f3faf5;padding:32px 16px;">
                    <tr>
                      <td align="center">
                        <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="max-width:520px;background-color:#ffffff;border-radius:20px;border:1px solid #dce9e2;box-shadow:0 10px 30px rgba(27,68,48,0.08);overflow:hidden;">
                          <tr>
                            <td style="height:6px;background:linear-gradient(90deg,#28694e 0%%,#1E8E4E 65%%,#FF7A1A 100%%);background-color:#28694e;font-size:0;line-height:0;">&nbsp;</td>
                          </tr>
                          <tr>
                            <td style="padding:28px 32px 16px 32px;">
                              <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0">
                                <tr>
                                  <td align="left" style="vertical-align:middle;">
                                    <span style="display:inline-block;font-size:22px;font-weight:700;letter-spacing:-0.3px;color:#173d2f;">Food<span style="color:#FF7A1A;">Rescue</span></span>
                                  </td>
                                  <td align="right" style="vertical-align:middle;">
                                    <span style="display:inline-block;padding:5px 12px;border-radius:999px;background-color:%s;color:%s;font-size:11px;font-weight:700;letter-spacing:0.6px;">%s</span>
                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:8px 32px 0 32px;">
                              <h1 style="margin:0 0 10px 0;font-size:22px;line-height:1.35;font-weight:700;color:#173d2f;">%s</h1>
                              <p style="margin:0 0 22px 0;font-size:15px;line-height:1.6;color:#4B5563;">%s</p>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:0 32px 22px 32px;">
                              <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="background-color:#f6fbf8;border:1.5px dashed #9fd0b6;border-radius:14px;">
                                <tr>
                                  <td align="center" style="padding:22px 16px;">
                                    <div style="margin:0 0 8px 0;font-size:12px;font-weight:600;text-transform:uppercase;letter-spacing:1px;color:#28694e;">Mã xác minh OTP</div>
                                    <div style="font-family:'SFMono-Regular',Consolas,'Courier New',monospace;font-size:34px;line-height:1.2;font-weight:700;letter-spacing:10px;padding-left:10px;color:#173d2f;">%s</div>
                                    <div style="margin:10px 0 0 0;font-size:13px;color:#6B7280;">Có hiệu lực trong <strong style="color:#1F1F1F;">10 phút</strong></div>
                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:0 32px 26px 32px;">
                              <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="background-color:#FAFAFA;border-radius:12px;border:1px solid #EEEEEE;">
                                <tr>
                                  <td style="padding:14px 16px;font-size:13px;line-height:1.55;color:#6B7280;">
                                    <div style="margin:0 0 6px 0;"><strong style="color:#E5484D;">Lưu ý bảo mật:</strong> Tuyệt đối không chia sẻ mã OTP này với bất kỳ ai.</div>
                                    <div>%s</div>
                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:16px 32px 24px 32px;border-top:1px solid #EEEEEE;text-align:center;font-size:12px;line-height:1.5;color:#9CA3AF;">
                              Email tự động từ hệ thống <strong style="color:#28694e;">FoodRescue</strong> · Vui lòng không trả lời email này.
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(subject, preheader, badgeBg, badgeColor, badgeLabel, heading, introHtml, code, ignoreNotice);
        return new EmailContent(subject, plainText, html);
    }

    public record EmailContent(String subject, String plainText, String html) {}
}
