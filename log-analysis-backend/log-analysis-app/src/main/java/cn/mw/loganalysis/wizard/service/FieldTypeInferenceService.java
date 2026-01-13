package cn.mw.loganalysis.wizard.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * 字段类型推断服务
 */
@Slf4j
@Service
public class FieldTypeInferenceService {

    // IP 地址正则
    private static final Pattern IP_PATTERN = Pattern.compile(
        "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    );

    // 时间戳正则（ISO 8601）
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile(
        "^\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}"
    );

    /**
     * 推断字段类型
     */
    public String inferType(Object value) {
        if (value == null) {
            return "String";
        }

        // 如果已经是强类型，直接返回
        if (value instanceof Integer || value instanceof Long) {
            return "Int32";
        }
        if (value instanceof Double || value instanceof Float) {
            return "Float64";
        }
        if (value instanceof Boolean) {
            return "UInt8"; // ClickHouse 使用 UInt8 表示布尔值
        }

        // 字符串类型需要进一步推断
        if (value instanceof String) {
            String str = (String) value;
            return inferStringType(str);
        }

        // 其他类型
        return "String";
    }

    /**
     * 推断字符串类型
     */
    private String inferStringType(String value) {
        if (value == null || value.isEmpty()) {
            return "String";
        }

        // 检查是否是时间戳
        if (TIMESTAMP_PATTERN.matcher(value).find()) {
            return "DateTime64";
        }

        // 检查是否是整数
        if (value.matches("^-?\\d+$")) {
            try {
                long num = Long.parseLong(value);
                if (num >= Integer.MIN_VALUE && num <= Integer.MAX_VALUE) {
                    return "Int32";
                } else {
                    return "Int64";
                }
            } catch (NumberFormatException e) {
                return "String";
            }
        }

        // 检查是否是浮点数
        if (value.matches("^-?\\d+\\.\\d+$")) {
            return "Float64";
        }

        // 检查是否是布尔值
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
            return "UInt8";
        }

        // 默认返回字符串
        return "String";
    }

    /**
     * 检测是否是 IP 地址
     */
    public boolean isIpAddress(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        return IP_PATTERN.matcher(value).matches();
    }

    /**
     * 获取字段类型建议
     * 
     * @param value 字段值
     * @return 类型建议，如果没有特殊建议则返回 null
     */
    public String getTypeSuggestion(Object value) {
        if (value instanceof String) {
            String str = (String) value;
            
            // IP 地址建议
            if (isIpAddress(str)) {
                return "IPv4";
            }
        }
        
        return null;
    }

    /**
     * 获取建议原因
     */
    public String getSuggestionReason(String suggestion) {
        if ("IPv4".equals(suggestion)) {
            return "检测到 IP 地址格式";
        }
        return null;
    }
}
