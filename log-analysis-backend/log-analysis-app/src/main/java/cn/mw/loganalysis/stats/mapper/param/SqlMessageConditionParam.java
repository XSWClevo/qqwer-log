package cn.mw.loganalysis.stats.mapper.param;

import lombok.Builder;
import lombok.Data;

/**
 * MyBatis 文本条件参数
 */
@Data
@Builder
public class SqlMessageConditionParam {

    private String operator;

    private String value;
}
