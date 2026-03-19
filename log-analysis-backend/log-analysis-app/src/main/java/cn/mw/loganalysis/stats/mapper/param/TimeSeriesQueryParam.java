package cn.mw.loganalysis.stats.mapper.param;

import lombok.Builder;
import lombok.Data;

/**
 * 时间序列查询参数
 */
@Data
@Builder
public class TimeSeriesQueryParam {

    private String tableName;

    private String startTime;

    private String endTime;

    private String timeBucketExpression;
}
