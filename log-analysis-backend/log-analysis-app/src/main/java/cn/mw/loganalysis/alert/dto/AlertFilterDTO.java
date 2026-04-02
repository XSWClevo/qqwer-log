package cn.mw.loganalysis.alert.dto;

import cn.mw.loganalysis.stats.dto.LogQueryRequest;
import lombok.Data;

import java.util.List;

/**
 * 告警过滤条件
 */
@Data
public class AlertFilterDTO {

    private List<LogQueryRequest.FieldFilter> fieldFilters;

    private List<LogQueryRequest.MessageCondition> messageConditions;

    private List<LogQueryRequest.MessageCondition> rawConditions;
}
