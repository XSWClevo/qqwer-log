package cn.mw.loganalysis.common.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * 日期时间工具类
 */
public final class DateTimeUtils {

    // ==================== 常用格式 ====================
    
    public static final String PATTERN_DATETIME = "yyyy-MM-dd HH:mm:ss";
    public static final String PATTERN_DATETIME_MILLIS = "yyyy-MM-dd HH:mm:ss.SSS";
    public static final String PATTERN_DATE = "yyyy-MM-dd";
    public static final String PATTERN_TIME = "HH:mm:ss";
    public static final String PATTERN_TIME_MILLIS = "HH:mm:ss.SSS";
    public static final String PATTERN_DATETIME_COMPACT = "yyyyMMddHHmmss";
    public static final String PATTERN_DATE_COMPACT = "yyyyMMdd";
    public static final String PATTERN_DATETIME_SLASH = "yyyy/MM/dd HH:mm:ss";
    public static final String PATTERN_DATE_SLASH = "yyyy/MM/dd";
    public static final String PATTERN_DATETIME_CN = "yyyy年MM月dd日 HH:mm:ss";
    public static final String PATTERN_DATE_CN = "yyyy年MM月dd日";

    // ==================== 预定义格式化器 ====================
    
    private static final DateTimeFormatter FORMATTER_DATETIME = DateTimeFormatter.ofPattern(PATTERN_DATETIME);
    private static final DateTimeFormatter FORMATTER_DATETIME_MILLIS = DateTimeFormatter.ofPattern(PATTERN_DATETIME_MILLIS);
    private static final DateTimeFormatter FORMATTER_DATE = DateTimeFormatter.ofPattern(PATTERN_DATE);
    private static final DateTimeFormatter FORMATTER_TIME = DateTimeFormatter.ofPattern(PATTERN_TIME);

    private DateTimeUtils() {
        // 工具类禁止实例化
    }

    // ==================== LocalDateTime 转字符串 ====================

    /**
     * LocalDateTime 转字符串（默认格式：yyyy-MM-dd HH:mm:ss）
     */
    public static String format(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(FORMATTER_DATETIME);
    }

    /**
     * LocalDateTime 转字符串（自定义格式）
     */
    public static String format(LocalDateTime dateTime, String pattern) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(DateTimeFormatter.ofPattern(pattern));
    }

    /**
     * LocalDateTime 转字符串（使用 DateTimeFormatter）
     */
    public static String format(LocalDateTime dateTime, DateTimeFormatter formatter) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(formatter);
    }

    /**
     * LocalDateTime 转字符串（带毫秒：yyyy-MM-dd HH:mm:ss.SSS）
     */
    public static String formatWithMillis(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(FORMATTER_DATETIME_MILLIS);
    }

    /**
     * LocalDateTime 转字符串（紧凑格式：yyyyMMddHHmmss）
     */
    public static String formatCompact(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return dateTime.format(DateTimeFormatter.ofPattern(PATTERN_DATETIME_COMPACT));
    }

    // ==================== LocalDate 转字符串 ====================

    /**
     * LocalDate 转字符串（默认格式：yyyy-MM-dd）
     */
    public static String format(LocalDate date) {
        if (date == null) {
            return null;
        }
        return date.format(FORMATTER_DATE);
    }

    /**
     * LocalDate 转字符串（自定义格式）
     */
    public static String format(LocalDate date, String pattern) {
        if (date == null) {
            return null;
        }
        return date.format(DateTimeFormatter.ofPattern(pattern));
    }

    /**
     * LocalDate 转字符串（紧凑格式：yyyyMMdd）
     */
    public static String formatCompact(LocalDate date) {
        if (date == null) {
            return null;
        }
        return date.format(DateTimeFormatter.ofPattern(PATTERN_DATE_COMPACT));
    }

    // ==================== LocalTime 转字符串 ====================

    /**
     * LocalTime 转字符串（默认格式：HH:mm:ss）
     */
    public static String format(LocalTime time) {
        if (time == null) {
            return null;
        }
        return time.format(FORMATTER_TIME);
    }

    /**
     * LocalTime 转字符串（自定义格式）
     */
    public static String format(LocalTime time, String pattern) {
        if (time == null) {
            return null;
        }
        return time.format(DateTimeFormatter.ofPattern(pattern));
    }

    // ==================== 字符串转 LocalDateTime ====================

    /**
     * 字符串转 LocalDateTime（默认格式：yyyy-MM-dd HH:mm:ss）
     */
    public static LocalDateTime parseDateTime(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        return LocalDateTime.parse(text, FORMATTER_DATETIME);
    }

    /**
     * 字符串转 LocalDateTime（自定义格式）
     */
    public static LocalDateTime parseDateTime(String text, String pattern) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        return LocalDateTime.parse(text, DateTimeFormatter.ofPattern(pattern));
    }

    // ==================== 字符串转 LocalDate ====================

    /**
     * 字符串转 LocalDate（默认格式：yyyy-MM-dd）
     */
    public static LocalDate parseDate(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        return LocalDate.parse(text, FORMATTER_DATE);
    }

    /**
     * 字符串转 LocalDate（自定义格式）
     */
    public static LocalDate parseDate(String text, String pattern) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        return LocalDate.parse(text, DateTimeFormatter.ofPattern(pattern));
    }

    // ==================== 字符串转 LocalTime ====================

    /**
     * 字符串转 LocalTime（默认格式：HH:mm:ss）
     */
    public static LocalTime parseTime(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        return LocalTime.parse(text, FORMATTER_TIME);
    }

    /**
     * 字符串转 LocalTime（自定义格式）
     */
    public static LocalTime parseTime(String text, String pattern) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        return LocalTime.parse(text, DateTimeFormatter.ofPattern(pattern));
    }

    // ==================== 便捷方法 ====================

    /**
     * 获取当前时间字符串（yyyy-MM-dd HH:mm:ss）
     */
    public static String now() {
        return format(LocalDateTime.now());
    }

    /**
     * 获取当前时间字符串（自定义格式）
     */
    public static String now(String pattern) {
        return format(LocalDateTime.now(), pattern);
    }

    /**
     * 获取当前日期字符串（yyyy-MM-dd）
     */
    public static String today() {
        return format(LocalDate.now());
    }

    /**
     * 获取当前日期字符串（自定义格式）
     */
    public static String today(String pattern) {
        return format(LocalDate.now(), pattern);
    }

    /**
     * 安全格式化（带默认值）
     */
    public static String formatOrDefault(LocalDateTime dateTime, String defaultValue) {
        return dateTime != null ? format(dateTime) : defaultValue;
    }

    /**
     * 安全格式化（带默认值和自定义格式）
     */
    public static String formatOrDefault(LocalDateTime dateTime, String pattern, String defaultValue) {
        return dateTime != null ? format(dateTime, pattern) : defaultValue;
    }
}
