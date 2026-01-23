package cn.mw.loganalysis.stats.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * AI自然语言查询请求
 *
 * @author Claude
 * @since 2026-01-22
 */
@Data
public class AiQueryRequest {

    /**
     * 自然语言查询
     */
    @NotBlank(message = "查询内容不能为空")
    @Size(min = 1, max = 1000, message = "查询内容长度必须在1-1000之间")
    private String query;

    /**
     * 数据源ID（可选，不传则使用默认syslog表）
     * 兼容单数据源查询
     */
    private String datasourceId;

    /**
     * 多数据源ID列表（可选，用于联合查询）
     * 优先级高于 datasourceId
     * 注意：所有数据源必须是同一类型（如都是 ClickHouse）
     */
    @Size(max = 10, message = "最多支持10个数据源联合查询")
    private List<String> datasourceIds;
}
