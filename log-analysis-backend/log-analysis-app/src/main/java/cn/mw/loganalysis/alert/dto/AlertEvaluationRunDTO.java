package cn.mw.loganalysis.alert.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 最近一次评估运行信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertEvaluationRunDTO {

    private Long id;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private LocalDateTime windowStart;

    private LocalDateTime windowEnd;

    private String status;

    private Integer matchedCount;

    private String errorMessage;

    private Map<String, Object> details;
}
