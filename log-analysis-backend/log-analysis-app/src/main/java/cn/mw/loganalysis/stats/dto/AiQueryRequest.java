package cn.mw.loganalysis.stats.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

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
     */
    private String datasourceId;
}
