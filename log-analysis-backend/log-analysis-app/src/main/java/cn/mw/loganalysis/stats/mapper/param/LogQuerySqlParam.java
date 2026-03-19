package cn.mw.loganalysis.stats.mapper.param;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 日志查询参数
 */
@Data
@Builder
public class LogQuerySqlParam {

    private String tableName;

    private String startTime;

    private String endTime;

    private Integer pageSize;

    private Integer offset;

    private List<SqlFieldFilterParam> fieldFilters;

    private List<SqlMessageConditionParam> messageConditions;

    private List<SqlMessageConditionParam> rawConditions;
}
