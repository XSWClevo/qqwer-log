package cn.mw.loganalysis.stats.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI自然语言查询响应
 *
 * @author Claude
 * @since 2026-01-22
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiQueryResponse {

    /**
     * 是否成功
     */
    private Boolean success;

    /**
     * 生成的SQL语句
     */
    private String sql;

    /**
     * 查询结果（由Java后端执行SQL后填充）
     */
    private Object result;

    /**
     * 错误信息
     */
    private String error;

    /**
     * SQL生成时间（秒）
     */
    private Double sqlGenerationTime;

    /**
     * SQL执行时间（秒）
     */
    private Double sqlExecutionTime;

    /**
     * 总执行时间（秒）
     */
    private Double totalExecutionTime;
}
