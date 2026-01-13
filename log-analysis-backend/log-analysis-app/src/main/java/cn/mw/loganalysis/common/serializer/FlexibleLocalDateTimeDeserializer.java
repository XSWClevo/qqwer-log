package cn.mw.loganalysis.common.serializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 灵活的 LocalDateTime 反序列化器
 * 支持多种日期时间格式：
 * - ISO 8601 格式：2026-01-04T15:15:27
 * - 空格分隔格式：2026-01-03 15:23:36
 * - 带毫秒的 ISO 8601：2026-01-04T15:15:27.123
 */
public class FlexibleLocalDateTimeDeserializer extends JsonDeserializer<LocalDateTime> {

    private static final DateTimeFormatter[] FORMATTERS = {
        // ISO 8601 格式（带 T）
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
        // 空格分隔格式
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        // 带毫秒的 ISO 8601
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"),
        // 带毫秒的空格分隔
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
        // ISO 标准格式
        DateTimeFormatter.ISO_LOCAL_DATE_TIME
    };

    @Override
    public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String dateString = p.getText();

        if (dateString == null || dateString.trim().isEmpty()) {
            return null;
        }

        // 尝试所有支持的格式
        for (DateTimeFormatter formatter : FORMATTERS) {
            try {
                return LocalDateTime.parse(dateString, formatter);
            } catch (DateTimeParseException e) {
                // 继续尝试下一个格式
            }
        }

        // 所有格式都失败，抛出异常
        throw new IOException(String.format(
            "无法解析日期时间: '%s'。支持的格式：" +
            "yyyy-MM-dd'T'HH:mm:ss, yyyy-MM-dd HH:mm:ss, yyyy-MM-dd'T'HH:mm:ss.SSS",
            dateString
        ));
    }
}
