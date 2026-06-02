package cn.mw.loganalysis.agent.support;

import org.apache.commons.lang3.StringUtils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class AgentToolSupport {

    private static final Pattern RELATIVE_RANGE_PATTERN = Pattern.compile("最近\\s*([0-9一二两三四五六七八九十半]+)\\s*(分钟|小时|天|周)");

    private AgentToolSupport() {
    }

    public static AgentTimeWindow resolveTimeWindow(String text, boolean defaultTimeseriesRange) {
        String normalized = normalizeText(text);
        LocalDateTime now = LocalDateTime.now();
        Matcher matcher = RELATIVE_RANGE_PATTERN.matcher(normalized);
        if (matcher.find()) {
            if ("半".equals(matcher.group(1)) && "小时".equals(matcher.group(2))) {
                return new AgentTimeWindow(now.minusMinutes(30), now, "最近30分钟");
            }
            Integer amount = parseChineseNumber(matcher.group(1));
            String unit = matcher.group(2);
            if (amount != null && amount > 0) {
                return switch (unit) {
                    case "分钟" -> new AgentTimeWindow(now.minusMinutes(amount), now, "最近" + amount + "分钟");
                    case "小时" -> new AgentTimeWindow(now.minusHours(amount), now, "最近" + amount + "小时");
                    case "天" -> new AgentTimeWindow(now.minusDays(amount), now, "最近" + amount + "天");
                    case "周" -> new AgentTimeWindow(now.minusWeeks(amount), now, "最近" + amount + "周");
                    default -> defaultWindow(now, defaultTimeseriesRange);
                };
            }
        }

        if (normalized.contains("今天")) {
            return new AgentTimeWindow(LocalDateTime.of(LocalDate.now(), LocalTime.MIN), now, "今天");
        }
        if (normalized.contains("昨天")) {
            LocalDate yesterday = LocalDate.now().minusDays(1);
            return new AgentTimeWindow(LocalDateTime.of(yesterday, LocalTime.MIN), LocalDateTime.of(yesterday, LocalTime.MAX), "昨天");
        }
        if (normalized.contains("最近一周") || normalized.contains("近7天")) {
            return new AgentTimeWindow(now.minusDays(7), now, "最近7天");
        }
        if (normalized.contains("最近24小时")) {
            return new AgentTimeWindow(now.minusHours(24), now, "最近24小时");
        }
        if (normalized.contains("最近1小时") || normalized.contains("近1小时")) {
            return new AgentTimeWindow(now.minusHours(1), now, "最近1小时");
        }
        if (normalized.contains("最近15分钟") || normalized.contains("近15分钟")) {
            return new AgentTimeWindow(now.minusMinutes(15), now, "最近15分钟");
        }

        return defaultWindow(now, defaultTimeseriesRange);
    }

    public static String resolveGranularity(String granularity, AgentTimeWindow timeWindow) {
        String normalized = normalizeText(granularity).toLowerCase(Locale.ROOT);
        if (Arrays.asList("1m", "5m", "1h", "1d").contains(normalized)) {
            return normalized;
        }
        if (normalized.contains("分钟")) {
            return normalized.contains("5") ? "5m" : "1m";
        }
        if (normalized.contains("小时")) {
            return "1h";
        }
        if (normalized.contains("天")) {
            return "1d";
        }

        long hours = Duration.between(timeWindow.start(), timeWindow.end()).toHours();
        if (hours <= 2) {
            return "1m";
        }
        if (hours <= 24) {
            return "5m";
        }
        if (hours <= 24 * 7L) {
            return "1h";
        }
        return "1d";
    }

    public static int clampLimit(Integer limit, int defaultSize, int maxSize) {
        if (limit == null || limit <= 0) {
            return defaultSize;
        }
        return Math.min(limit, maxSize);
    }

    public static String normalizeText(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }

    public static boolean containsAny(String text, String... keywords) {
        return Arrays.stream(keywords).anyMatch(text::contains);
    }

    public static Integer parseChineseNumber(String token) {
        if (StringUtils.isBlank(token)) {
            return null;
        }
        return switch (token) {
            case "半" -> 30;
            case "一" -> 1;
            case "二", "两" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            case "六" -> 6;
            case "七" -> 7;
            case "八" -> 8;
            case "九" -> 9;
            case "十" -> 10;
            default -> {
                try {
                    yield Integer.parseInt(token);
                } catch (NumberFormatException ex) {
                    yield null;
                }
            }
        };
    }

    public static String joinOrFallback(List<String> values, String fallback) {
        if (values == null || values.isEmpty()) {
            return fallback;
        }
        return String.join("、", values);
    }

    public static String stringify(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    public static String truncate(String value, int maxLength) {
        if (StringUtils.isBlank(value) || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> castList(Object value) {
        if (value instanceof List<?>) {
            return ((List<?>) value).stream()
                    .filter(Map.class::isInstance)
                    .map(item -> (Map<String, Object>) item)
                    .collect(Collectors.toCollection(ArrayList::new));
        }
        return new ArrayList<>();
    }

    public static long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    public static double safeDouble(Double value) {
        return value == null ? 0D : value;
    }

    private static AgentTimeWindow defaultWindow(LocalDateTime now, boolean defaultTimeseriesRange) {
        if (defaultTimeseriesRange) {
            return new AgentTimeWindow(now.minusHours(24), now, "最近24小时");
        }
        return new AgentTimeWindow(now.minusHours(1), now, "最近1小时");
    }
}
