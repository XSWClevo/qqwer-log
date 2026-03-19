package cn.mw.loganalysis.stats.mapper.param;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * MyBatis 字段过滤参数
 */
@Data
@Builder
public class SqlFieldFilterParam {

    private String columnExpression;

    private String type;

    private List<String> values;
}
