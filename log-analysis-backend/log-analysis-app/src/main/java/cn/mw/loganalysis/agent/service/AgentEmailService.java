package cn.mw.loganalysis.agent.service;

import cn.mw.loganalysis.agent.dto.AgentEmailRequest;
import cn.mw.loganalysis.agent.dto.AgentEmailResponse;
import cn.mw.loganalysis.agent.dto.AgentToolCall;
import cn.mw.loganalysis.auth.entity.User;
import cn.mw.loganalysis.auth.service.AuthService;
import cn.mw.loganalysis.common.exception.ValidationException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 智能助手结果邮件服务。
 *
 * 这条链路故意做成“当前用户触发 -> 发到当前用户自己的邮箱”：
 * 1. 不需要前端再额外传收件人，避免随意向外发邮件
 * 2. 收件地址直接以当前登录用户资料为准，便于审计和控制
 * 3. SMTP 未配置时直接返回明确错误，不做假发送
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentEmailService {

    private static final Pattern MARKDOWN_LINK_PATTERN = Pattern.compile("\\[([^\\]]+)]\\(([^)]+)\\)");
    private static final Pattern INLINE_CODE_PATTERN = Pattern.compile("`([^`]+)`");
    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern ITALIC_PATTERN = Pattern.compile("(?<!\\*)\\*([^*\\n]+)\\*(?!\\*)");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AuthService authService;
    private final MailProperties mailProperties;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${agent.email.from:}")
    private String configuredFrom;

    public AgentEmailResponse sendToCurrentUser(Long userId, AgentEmailRequest request) {
        User user = authService.getUserInfo(userId);
        if (!StringUtils.hasText(user.getEmail())) {
            throw new ValidationException("当前用户未配置邮箱，无法发送智能助手结果");
        }
        if (!StringUtils.hasText(mailProperties.getHost())) {
            throw new ValidationException("邮件服务未配置，请先设置 MAIL_HOST、MAIL_USERNAME、MAIL_PASSWORD");
        }

        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null) {
            throw new ValidationException("邮件发送器未初始化，请检查 SMTP 配置");
        }

        String subject = buildSubject(request);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            if (StringUtils.hasText(resolveFromAddress())) {
                helper.setFrom(resolveFromAddress());
            }
            helper.setTo(user.getEmail());
            helper.setSubject(subject);
            helper.setText(buildPlainTextBody(request), buildHtmlBody(user, request));
            mailSender.send(message);
        } catch (Exception ex) {
            log.error("发送智能助手结果邮件失败, userId={}, email={}", userId, user.getEmail(), ex);
            throw new ValidationException("邮件发送失败: " + ex.getMessage(), ex);
        }

        log.info("智能助手结果邮件发送成功, userId={}, email={}, subject={}", userId, user.getEmail(), subject);
        return AgentEmailResponse.builder()
                .recipient(user.getEmail())
                .subject(subject)
                .sentAt(LocalDateTime.now())
                .build();
    }

    private String resolveFromAddress() {
        if (StringUtils.hasText(configuredFrom)) {
            return configuredFrom.trim();
        }
        if (StringUtils.hasText(mailProperties.getUsername())) {
            return mailProperties.getUsername().trim();
        }
        return null;
    }

    private String buildSubject(AgentEmailRequest request) {
        if (StringUtils.hasText(request.getConversationTitle())) {
            return "[日志助手] " + abbreviate(request.getConversationTitle().trim(), 72);
        }
        if (StringUtils.hasText(request.getDatasourceName())) {
            return "[日志助手] " + request.getDatasourceName() + " 查询结果";
        }
        return "[日志助手] 查询结果";
    }

    private String buildPlainTextBody(AgentEmailRequest request) {
        StringBuilder builder = new StringBuilder();
        builder.append("智能助手结果").append("\n\n");
        if (StringUtils.hasText(request.getConversationTitle())) {
            builder.append("会话：").append(request.getConversationTitle()).append("\n");
        }
        if (StringUtils.hasText(request.getDatasourceName())) {
            builder.append("数据源：").append(request.getDatasourceName()).append("\n");
        }
        if (StringUtils.hasText(request.getSessionId())) {
            builder.append("会话ID：").append(request.getSessionId()).append("\n");
        }
        builder.append("发送时间：").append(LocalDateTime.now().format(TIME_FORMATTER)).append("\n\n");
        builder.append(request.getContent().trim()).append("\n");

        appendToolSummary(builder, request.getToolCalls());
        return builder.toString();
    }

    private String buildHtmlBody(User user, AgentEmailRequest request) {
        StringBuilder builder = new StringBuilder();
        builder.append("<html><body style=\"margin:0;padding:24px;background:#f8fafc;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;color:#0f172a;\">");
        builder.append("<div style=\"max-width:880px;margin:0 auto;background:#ffffff;border:1px solid #e2e8f0;border-radius:20px;overflow:hidden;\">");
        builder.append("<div style=\"padding:20px 24px;background:linear-gradient(135deg,#082f49,#0f766e);color:#f8fafc;\">");
        builder.append("<div style=\"font-size:12px;letter-spacing:0.08em;text-transform:uppercase;opacity:0.82;\">Smart Agent Mail</div>");
        builder.append("<h2 style=\"margin:8px 0 0;font-size:26px;line-height:1.15;\">").append(HtmlUtils.htmlEscape(buildSubject(request))).append("</h2>");
        builder.append("</div>");
        builder.append("<div style=\"padding:24px;\">");
        builder.append("<p style=\"margin:0 0 18px;color:#475569;line-height:1.7;\">")
                .append("这封邮件由日志助手发送给当前登录用户 ")
                .append("<strong>").append(HtmlUtils.htmlEscape(user.getUsername())).append("</strong>")
                .append("，用于回收查询结果。</p>");
        builder.append("<div style=\"display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:12px;margin-bottom:20px;\">");
        appendMetaCard(builder, "数据源", request.getDatasourceName(), "未提供");
        appendMetaCard(builder, "会话标题", request.getConversationTitle(), "未命名对话");
        appendMetaCard(builder, "发送时间", LocalDateTime.now().format(TIME_FORMATTER), "-");
        builder.append("</div>");
        builder.append("<div style=\"padding:18px 20px;border:1px solid #e2e8f0;border-radius:18px;background:#f8fafc;line-height:1.8;\">");
        builder.append(renderMarkdownToHtml(request.getContent()));
        builder.append("</div>");

        if (request.getToolCalls() != null && !request.getToolCalls().isEmpty()) {
            builder.append("<div style=\"margin-top:20px;\">");
            builder.append("<h3 style=\"margin:0 0 12px;font-size:18px;\">工具执行摘要</h3>");
            builder.append("<ul style=\"margin:0;padding-left:18px;color:#334155;line-height:1.7;\">");
            for (AgentToolCall toolCall : request.getToolCalls()) {
                String summary = StringUtils.hasText(toolCall.getSummary()) ? toolCall.getSummary() : toolCall.getToolName();
                builder.append("<li><strong>")
                        .append(HtmlUtils.htmlEscape(toolCall.getToolLabel()))
                        .append("</strong>：")
                        .append(HtmlUtils.htmlEscape(summary));
                if (toolCall.getDurationMs() != null) {
                    builder.append(" (").append(toolCall.getDurationMs()).append(" ms)");
                }
                builder.append("</li>");
            }
            builder.append("</ul></div>");
        }

        builder.append("</div></div></body></html>");
        return builder.toString();
    }

    private void appendMetaCard(StringBuilder builder, String label, String value, String fallback) {
        builder.append("<div style=\"padding:14px 16px;border-radius:16px;background:#f8fafc;border:1px solid #e2e8f0;\">");
        builder.append("<div style=\"font-size:12px;letter-spacing:0.05em;text-transform:uppercase;color:#64748b;\">")
                .append(HtmlUtils.htmlEscape(label))
                .append("</div>");
        builder.append("<div style=\"margin-top:8px;font-size:15px;font-weight:600;color:#0f172a;\">")
                .append(HtmlUtils.htmlEscape(StringUtils.hasText(value) ? value : fallback))
                .append("</div>");
        builder.append("</div>");
    }

    private void appendToolSummary(StringBuilder builder, List<AgentToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return;
        }
        builder.append("\n工具执行摘要：\n");
        for (AgentToolCall toolCall : toolCalls) {
            String summary = StringUtils.hasText(toolCall.getSummary()) ? toolCall.getSummary() : toolCall.getToolName();
            builder.append("- ").append(toolCall.getToolLabel()).append(": ").append(summary);
            if (toolCall.getDurationMs() != null) {
                builder.append(" (").append(toolCall.getDurationMs()).append(" ms)");
            }
            builder.append("\n");
        }
    }

    /**
     * 邮件正文不需要完整 Markdown 规范，只要把常见的强调、链接和换行转成可读 HTML 即可。
     * 这里仍然先做 HTML 转义，避免直接把模型输出拼进邮件模板。
     */
    private String renderMarkdownToHtml(String markdown) {
        String escaped = HtmlUtils.htmlEscape(markdown == null ? "" : markdown.trim());
        escaped = replacePattern(escaped, MARKDOWN_LINK_PATTERN, (matcher) ->
                "<a href=\"" + sanitizeUrl(matcher.group(2)) + "\" target=\"_blank\" rel=\"noreferrer noopener\" style=\"color:#0f766e;text-decoration:none;\">"
                        + HtmlUtils.htmlEscape(matcher.group(1)) + "</a>");
        escaped = replacePattern(escaped, INLINE_CODE_PATTERN, (matcher) ->
                "<code style=\"padding:2px 6px;border-radius:8px;background:#e2e8f0;font-family:SFMono-Regular,monospace;\">"
                        + HtmlUtils.htmlEscape(matcher.group(1)) + "</code>");
        escaped = replacePattern(escaped, BOLD_PATTERN, (matcher) -> "<strong>" + matcher.group(1) + "</strong>");
        escaped = replacePattern(escaped, ITALIC_PATTERN, (matcher) -> "<em>" + matcher.group(1) + "</em>");

        String[] paragraphs = escaped.split("\\n\\s*\\n");
        StringBuilder html = new StringBuilder();
        for (String paragraph : paragraphs) {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            html.append("<p style=\"margin:0 0 14px;\">")
                    .append(trimmed.replace("\n", "<br/>"))
                    .append("</p>");
        }
        return html.toString();
    }

    private String replacePattern(String input, Pattern pattern, Replacement replacement) {
        StringBuilder builder = new StringBuilder();
        Matcher matcher = pattern.matcher(input);
        while (matcher.find()) {
            matcher.appendReplacement(builder, Matcher.quoteReplacement(replacement.replace(matcher)));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

    private String sanitizeUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return "#";
        }
        String normalized = url.trim();
        return normalized.startsWith("http://")
                || normalized.startsWith("https://")
                || normalized.startsWith("mailto:")
                ? HtmlUtils.htmlEscape(normalized)
                : "#";
    }

    private String abbreviate(String text, int maxLength) {
        if (!StringUtils.hasText(text) || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, Math.max(0, maxLength - 1)) + "…";
    }

    @FunctionalInterface
    private interface Replacement {
        String replace(Matcher matcher);
    }
}
