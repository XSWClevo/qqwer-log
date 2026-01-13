package cn.mw.loganalysis.operationlog.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 操作统计 DTO
 *
 * @author Claude
 * @since 2026-01-07
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationStatsDTO {

    /**
     * 统计维度名称
     */
    private String name;

    /**
     * 统计数量
     */
    private Long count;

    /**
     * 成功数量
     */
    private Long successCount;

    /**
     * 失败数量
     */
    private Long failureCount;

    /**
     * 成功率 (百分比)
     */
    private Double successRate;
}
