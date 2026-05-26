package cn.mw.loganalysis.alert.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Monitor 当前状态展示 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertMonitorStateDTO {

    private String state;

    private String previousState;

    private String datasourceId;

    private String tableName;

    private String groupKey;

    private Map<String, Object> groupValues;

    private BigDecimal lastValue;

    private BigDecimal lastThreshold;

    private LocalDateTime lastEvaluatedAt;

    private LocalDateTime lastStateChangedAt;

    private LocalDateTime lastNotifiedAt;

    private Integer renotifyCount;
}
