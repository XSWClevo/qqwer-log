package cn.mw.loganalysis.agent.service;

import cn.mw.loganalysis.common.util.DateTimeUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * Text2SQL 模板候选辅助方法。
 */
final class SqlTemplateSupport {

    private SqlTemplateSupport() {
    }

    static String quoteIdentifier(String value) {
        return "`" + StringUtils.replace(StringUtils.defaultString(value), "`", "") + "`";
    }

    static String literal(String value) {
        return "'" + StringUtils.replace(StringUtils.defaultString(value), "'", "''") + "'";
    }

    static String timeWhere(AgentTimeWindow timeWindow) {
        return " WHERE `timestamp` >= " + literal(DateTimeUtils.format(timeWindow.start()))
                + " AND `timestamp` <= " + literal(DateTimeUtils.format(timeWindow.end()));
    }

    static String severityClause(String severity) {
        if (StringUtils.isBlank(severity)) {
            return "";
        }
        return " AND `severity` IN (" + String.join(", ", resolveSeverityValues(severity).stream()
                .map(SqlTemplateSupport::literal)
                .toList()) + ")";
    }

    static List<String> resolveSeverityValues(String severity) {
        String normalized = StringUtils.lowerCase(severity, Locale.ROOT);
        if (AgentToolSupport.containsAny(normalized, "warn", "告警", "警告")) {
            return List.of("warning", "warn", "WARN", "WARNING");
        }
        if (AgentToolSupport.containsAny(normalized, "error", "错误", "异常")) {
            return List.of("error", "ERROR");
        }
        if (AgentToolSupport.containsAny(normalized, "info", "信息")) {
            return List.of("info", "INFO");
        }
        if (AgentToolSupport.containsAny(normalized, "debug", "调试")) {
            return List.of("debug", "DEBUG");
        }
        return List.of(severity);
    }

    static String resolveDimension(String query) {
        String lower = StringUtils.lowerCase(AgentToolSupport.normalizeText(query), Locale.ROOT);
        if (AgentToolSupport.containsAny(lower, "severity", "level", "级别", "等级")) {
            return "severity";
        }
        if (AgentToolSupport.containsAny(lower, "hostname", "host", "主机")) {
            return "hostname";
        }
        if (AgentToolSupport.containsAny(lower, "appname", "service", "服务", "应用")) {
            return "appname";
        }
        if (AgentToolSupport.containsAny(lower, "facility")) {
            return "facility";
        }
        if (AgentToolSupport.containsAny(lower, "source_type", "来源类型")) {
            return "source_type";
        }
        return null;
    }
}
