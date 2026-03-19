package cn.mw.loganalysis.alert.mapper;

import cn.mw.loganalysis.alert.entity.AlertEvent;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 告警事件Mapper
 * 使用PostgreSQL数据源存储告警事件记录
 */
@Mapper
@DS("postgres")
public interface AlertEventMapper extends BaseMapper<AlertEvent> {

    /**
     * 获取规则最后触发时间
     */
    LocalDateTime getLastTriggeredTime(@Param("ruleId") Long ruleId);

    /**
     * 获取规则触发次数
     */
    Long getTriggerCount(@Param("ruleId") Long ruleId);

    /**
     * 分页查询告警事件
     *
     * @param page 分页参数
     * @param keyword 关键词（搜索规则名称和消息）
     * @param severity 严重程度
     * @param ruleId 规则ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 分页结果
     */
    default IPage<AlertEvent> selectPageByCondition(Page<AlertEvent> page, String keyword,
                                                      String severity, Long ruleId,
                                                      LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<AlertEvent> wrapper = new LambdaQueryWrapper<>();

        // 时间范围过滤
        wrapper.between(AlertEvent::getTriggeredAt, startTime, endTime);

        // 关键词搜索
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(AlertEvent::getRuleName, keyword)
                    .or().like(AlertEvent::getMessage, keyword));
        }

        // 严重程度过滤
        if (StringUtils.hasText(severity)) {
            wrapper.eq(AlertEvent::getSeverity, severity.toUpperCase());
        }

        // 规则ID过滤
        if (ruleId != null) {
            wrapper.eq(AlertEvent::getRuleId, ruleId);
        }

        wrapper.orderByDesc(AlertEvent::getTriggeredAt);

        return selectPage(page, wrapper);
    }
}
