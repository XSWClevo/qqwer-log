package cn.mw.loganalysis.stats.mapper.param;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 上下文日志查询参数
 */
@Data
@Builder
public class ContextLogQueryParam {

    private String tableName;

    private String timestamp;

    private Integer limit;

    private List<SqlFieldFilterParam> fieldFilters;

    private List<SqlMessageConditionParam> messageConditions;

    private List<SqlMessageConditionParam> rawConditions;
}
