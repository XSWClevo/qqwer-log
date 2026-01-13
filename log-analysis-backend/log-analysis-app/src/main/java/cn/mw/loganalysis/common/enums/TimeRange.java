package cn.mw.loganalysis.common.enums;

import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 时间范围枚举
 */
@Getter
public enum TimeRange {
    CUSTOM("custom", 0, "自定义"),
    LAST_24H("24h", 24, "最近24小时"),
    LAST_7D("7d", 7 * 24, "最近7天"),
    LAST_30D("30d", 30 * 24, "最近30天");

    private final String code;
    private final int hours;
    private final String description;

    TimeRange(String code, int hours, String description) {
        this.code = code;
        this.hours = hours;
        this.description = description;
    }

    public static TimeRange fromCode(String code) {
        for (TimeRange range : values()) {
            if (range.code.equals(code)) {
                return range;
            }
        }
        return LAST_24H;
    }

    /**
     * 计算开始时间
     */
    public LocalDateTime getStartTime(LocalDateTime now) {
        if (this == CUSTOM) {
            throw new IllegalStateException("自定义时间范围需要手动指定开始时间");
        }
        return now.minusHours(hours);
    }
}
