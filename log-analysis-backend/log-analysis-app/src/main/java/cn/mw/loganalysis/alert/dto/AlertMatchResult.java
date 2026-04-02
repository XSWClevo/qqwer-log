package cn.mw.loganalysis.alert.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 告警命中结果
 */
@Data
@Builder
public class AlertMatchResult {

    private String datasourceId;

    private String tableName;

    private BigDecimal actualValue;

    private BigDecimal threshold;

    private Map<String, Object> groupValues;

    private List<Map<String, Object>> samples;
}
