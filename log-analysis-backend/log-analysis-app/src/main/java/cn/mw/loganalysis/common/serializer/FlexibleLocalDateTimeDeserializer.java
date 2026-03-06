package cn.mw.loganalysis.common.serializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 灵活的 LocalDateTime 反序列化器
 * 支持多种日期时间格式：
 * - ISO 8601 格式：2026-01-04T15:15:27
 * - 空格分隔格式：2026-01-03 15:23:36
 * - 带毫秒的 ISO 8601：2026-01-04T15:15:27.123
 * - 带时区的 ISO 8601：2026-01-04T15:15:27Z
 * - 数组格式：[2026, 1, 4, 15, 15, 27, 123000000]
 * - 时间戳毫秒值：1735974927123
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
        JsonToken token = p.currentToken();
        if (token == JsonToken.VALUE_NULL) {
            return null;
        }

        if (token == JsonToken.START_ARRAY) {
            return parseArrayValue(p);
        }

        if (token == JsonToken.VALUE_NUMBER_INT) {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(p.getLongValue()), ZoneId.systemDefault());
        }

        return parseTextValue(p.getValueAsString());
    }

    private LocalDateTime parseArrayValue(JsonParser p) throws IOException {
        JsonNode node = p.readValueAsTree();
        if (node == null || !node.isArray() || node.isEmpty()) {
            return null;
        }

        if (node.size() == 1 && node.get(0).isTextual()) {
            return parseTextValue(node.get(0).asText());
        }

        if (node.size() < 3) {
            throw new IOException("无法解析日期时间数组，至少需要 [year, month, day]");
        }

        int year = node.get(0).asInt();
        int month = node.get(1).asInt();
        int day = node.get(2).asInt();
        int hour = node.size() > 3 ? node.get(3).asInt() : 0;
        int minute = node.size() > 4 ? node.get(4).asInt() : 0;
        int second = node.size() > 5 ? node.get(5).asInt() : 0;
        int nano = node.size() > 6 ? node.get(6).asInt() : 0;

        try {
            return LocalDateTime.of(year, month, day, hour, minute, second, nano);
        } catch (RuntimeException ex) {
            throw new IOException("无法解析日期时间数组: " + node, ex);
        }
    }

    private LocalDateTime parseTextValue(String dateString) throws IOException {

        if (dateString == null || dateString.trim().isEmpty()) {
            return null;
        }

        String normalized = dateString.trim();

        // 尝试所有支持的格式
        for (DateTimeFormatter formatter : FORMATTERS) {
            try {
                return LocalDateTime.parse(normalized, formatter);
            } catch (DateTimeParseException e) {
                // 继续尝试下一个格式
            }
        }

        try {
            return OffsetDateTime.parse(normalized, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    .atZoneSameInstant(ZoneId.systemDefault())
                    .toLocalDateTime();
        } catch (DateTimeParseException e) {
            // ignore
        }

        try {
            return LocalDateTime.ofInstant(Instant.parse(normalized), ZoneId.systemDefault());
        } catch (DateTimeParseException e) {
            // ignore
        }

        // 所有格式都失败，抛出异常
        throw new IOException(String.format(
            "无法解析日期时间: '%s'。支持的格式：" +
            "yyyy-MM-dd'T'HH:mm:ss, yyyy-MM-dd HH:mm:ss, yyyy-MM-dd'T'HH:mm:ss.SSS, yyyy-MM-dd'T'HH:mm:ssZ, [year,month,day,hour,minute,second,nano], epochMillis",
            normalized
        ));
    }
}
