package cn.mw.loganalysis.stats.service.query;

import java.time.temporal.TemporalAccessor;
import java.util.List;

/**
 * 把参数化 SQL 渲染成便于人工复制执行的文本。
 *
 * 这个类最初是为了调试日志引入的，但现在也复用于 ClickHouse MCP 桥接。
 * 因为官方 mcp-clickhouse 的查询工具接收的是完整 SQL 文本，不是参数化语句，
 * 所以这里提供一个统一的“参数 SQL -> 可执行 SQL”渲染器。
 */
public final class SqlDebugFormatter {

    private SqlDebugFormatter() {
    }

    public static String render(String sql, List<?> params) {
        if (sql == null) {
            return "";
        }

        StringBuilder rendered = new StringBuilder(sql.length() + 64);
        int paramIndex = 0;
        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            if (ch == '?' && paramIndex < params.size()) {
                rendered.append(formatValue(params.get(paramIndex++)));
            } else {
                rendered.append(ch);
            }
        }

        if (paramIndex < params.size()) {
            rendered.append(" /* unused params: ").append(params.subList(paramIndex, params.size())).append(" */");
        }
        return rendered.toString();
    }

    private static String formatValue(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof TemporalAccessor || value instanceof java.util.Date || value instanceof Enum<?> || value instanceof CharSequence) {
            return '\'' + escape(String.valueOf(value)) + '\'';
        }
        return '\'' + escape(String.valueOf(value)) + '\'';
    }

    private static String escape(String value) {
        return value.replace("'", "''");
    }
}
