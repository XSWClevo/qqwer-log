package cn.mw.loganalysis.agent.service;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class TimeseriesIntentMatcher implements AgentIntentMatcher {

    /**
     * 在结构查询之后匹配趋势/时序类问题。
     */
    @Override
    public int getOrder() {
        return 30;
    }

    /**
     * 判断消息是否表达趋势、曲线或按时间聚合的分析诉求。
     */
    @Override
    public boolean matches(AgentRuntimeContext context) {
        String lower = StringUtils.lowerCase(context.getEffectiveMessage(), Locale.ROOT);
        return AgentToolSupport.containsAny(lower, "趋势", "时序", "波动", "曲线", "每小时", "每分钟", "走势图");
    }

    /**
     * 返回 TIMESERIES 意图决策。
     */
    @Override
    public AgentIntentDecision match(AgentRuntimeContext context) {
        return new AgentIntentDecision(AgentIntent.TIMESERIES, context.getEffectiveMessage(), null, null, true);
    }
}
