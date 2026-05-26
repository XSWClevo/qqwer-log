package cn.mw.loganalysis.alert.dto;

import lombok.Data;

import java.util.List;

/**
 * Datadog Monitor 风格的高级评估与通知选项。
 */
@Data
public class AlertMonitorOptionsDTO {

    private Boolean notifyNoData;

    private String noDataTimeframe;

    private Boolean requireFullWindow;

    private Integer evaluationDelaySeconds;

    private Integer newGroupDelaySeconds;

    private Integer renotifyIntervalMinutes;

    private Integer renotifyOccurrences;

    private Boolean includeTags;

    private String priority;

    private String team;

    private List<String> tags;

    private String alertMode;

    private String escalationMessage;
}
