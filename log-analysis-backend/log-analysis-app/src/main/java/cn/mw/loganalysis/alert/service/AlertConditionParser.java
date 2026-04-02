package cn.mw.loganalysis.alert.service;

import cn.mw.loganalysis.alert.dto.AlertAggregateDTO;
import cn.mw.loganalysis.alert.dto.AlertConditionDTO;
import cn.mw.loganalysis.alert.dto.AlertFilterDTO;
import cn.mw.loganalysis.alert.dto.AlertTriggerDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 告警条件解析器
 */
@Component
@RequiredArgsConstructor
public class AlertConditionParser {

    private final ObjectMapper objectMapper;

    public AlertConditionDTO parse(Map<String, Object> condition) {
        AlertConditionDTO result = new AlertConditionDTO();
        if (MapUtils.isEmpty(condition)) {
            return result;
        }

        // 先尝试解析新版结构
        if (condition.containsKey("filters") || condition.containsKey("aggregate") || condition.containsKey("trigger")) {
            result = objectMapper.convertValue(condition, AlertConditionDTO.class);
        }

        // 兼容旧版 query / metric / operator / value / timeWindow / groupBy
        if (StringUtils.isBlank(result.getQuery())) {
            result.setQuery(MapUtils.getString(condition, "query"));
        }

        if (result.getAggregate() == null) {
            AlertAggregateDTO aggregate = new AlertAggregateDTO();
            aggregate.setFunction(MapUtils.getString(condition, "metric", "count"));
            aggregate.setField(MapUtils.getString(condition, "field", "*"));
            result.setAggregate(aggregate);
        }

        if (result.getTrigger() == null) {
            AlertTriggerDTO trigger = new AlertTriggerDTO();
            trigger.setOperator(MapUtils.getString(condition, "operator", "gt"));
            Object threshold = condition.get("value");
            if (threshold != null) {
                trigger.setThreshold(new BigDecimal(String.valueOf(threshold)));
            }
            trigger.setTimeWindow(MapUtils.getString(condition, "timeWindow", "5m"));
            result.setTrigger(trigger);
        }

        if (CollectionUtils.isEmpty(result.getGroupBy())) {
            String groupBy = MapUtils.getString(condition, "groupBy");
            if (StringUtils.isNotBlank(groupBy)) {
                result.setGroupBy(List.of(groupBy));
            }
        }

        if (result.getFilters() == null) {
            AlertFilterDTO filters = new AlertFilterDTO();
            if (condition.containsKey("fieldFilters")) {
                filters.setFieldFilters(objectMapper.convertValue(condition.get("fieldFilters"),
                        objectMapper.getTypeFactory().constructCollectionType(List.class,
                                cn.mw.loganalysis.stats.dto.LogQueryRequest.FieldFilter.class)));
            }
            if (condition.containsKey("messageConditions")) {
                filters.setMessageConditions(objectMapper.convertValue(condition.get("messageConditions"),
                        objectMapper.getTypeFactory().constructCollectionType(List.class,
                                cn.mw.loganalysis.stats.dto.LogQueryRequest.MessageCondition.class)));
            }
            if (condition.containsKey("rawConditions")) {
                filters.setRawConditions(objectMapper.convertValue(condition.get("rawConditions"),
                        objectMapper.getTypeFactory().constructCollectionType(List.class,
                                cn.mw.loganalysis.stats.dto.LogQueryRequest.MessageCondition.class)));
            }
            result.setFilters(filters);
        }

        if (result.getAggregate() != null && StringUtils.isBlank(result.getAggregate().getFunction())) {
            result.getAggregate().setFunction("count");
        }
        if (result.getAggregate() != null && StringUtils.isBlank(result.getAggregate().getField())) {
            result.getAggregate().setField("*");
        }
        if (result.getTrigger() != null && StringUtils.isBlank(result.getTrigger().getTimeWindow())) {
            result.getTrigger().setTimeWindow("5m");
        }
        if (result.getTrigger() != null && result.getTrigger().getThreshold() == null) {
            result.getTrigger().setThreshold(BigDecimal.ZERO);
        }
        if (result.getTrigger() != null && StringUtils.isBlank(result.getTrigger().getOperator())) {
            result.getTrigger().setOperator("gt");
        }
        return result;
    }
}
