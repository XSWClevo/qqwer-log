package cn.mw.loganalysis.alert.mapper;

import cn.mw.loganalysis.alert.entity.AlertRule;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.util.StringUtils;

/**
 * 告警规则Mapper
 * 使用PostgreSQL数据源存储告警规则配置
 */
@Mapper
@DS("postgres")
public interface AlertRuleMapper extends BaseMapper<AlertRule> {

    /**
     * 分页查询告警规则
     *
     * @param page 分页参数
     * @param keyword 关键词（搜索名称和描述）
     * @param status 状态（enabled/disabled）
     * @param severity 严重程度
     * @return 分页结果
     */
    default IPage<AlertRule> selectPageByCondition(Page<AlertRule> page, String keyword,
                                                     String status, String severity) {
        LambdaQueryWrapper<AlertRule> wrapper = new LambdaQueryWrapper<>();

        // 关键词搜索
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(AlertRule::getName, keyword)
                    .or().like(AlertRule::getDescription, keyword));
        }

        // 状态过滤
        if ("enabled".equals(status)) {
            wrapper.eq(AlertRule::getEnabled, true);
        } else if ("disabled".equals(status)) {
            wrapper.eq(AlertRule::getEnabled, false);
        }

        // 严重程度过滤
        if (StringUtils.hasText(severity)) {
            wrapper.eq(AlertRule::getSeverity, severity.toUpperCase());
        }

        wrapper.orderByDesc(AlertRule::getCreatedAt);

        return selectPage(page, wrapper);
    }

    /**
     * 查询启用的告警规则
     *
     * @return 启用的规则列表
     */
    default java.util.List<AlertRule> selectEnabledRules() {
        LambdaQueryWrapper<AlertRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlertRule::getEnabled, true)
               .orderByDesc(AlertRule::getCreatedAt);
        return selectList(wrapper);
    }
}

