package cn.mw.loganalysis.stats.mapper.param;

import lombok.Builder;
import lombok.Data;

/**
 * 维度统计查询参数
 */
@Data
@Builder
public class DimensionStatsQueryParam {

    private String tableName;

    private String startTime;

    private String endTime;

    private String dimensionExpression;
}
