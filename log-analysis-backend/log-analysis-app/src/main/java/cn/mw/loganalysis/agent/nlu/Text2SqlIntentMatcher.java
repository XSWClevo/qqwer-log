package cn.mw.loganalysis.agent.nlu;

import cn.mw.loganalysis.agent.execution.AgentRuntimeContext;
import cn.mw.loganalysis.agent.support.AgentToolSupport;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class Text2SqlIntentMatcher implements AgentIntentMatcher {

    /**
     * 在时序意图之后匹配 ClickHouse 聚合统计问题。
     */
    @Override
    public int getOrder() {
        return 40;
    }

    /**
     * 判断当前 ClickHouse 数据源下是否适合走 Text2SQL。
     */
    @Override
    public boolean matches(AgentRuntimeContext context) {
        if (context.getDatasource() == null || !StringUtils.equalsIgnoreCase(context.getDatasource().getVectorType(), "clickhouse")) {
            return false;
        }
        String lower = StringUtils.lowerCase(context.getEffectiveMessage(), Locale.ROOT);
        return AgentToolSupport.containsAny(lower,
                "多少条", "多少", "统计", "总数", "数量", "汇总", "排行", "top", "分组", "占比", "平均", "avg", "sum", "max", "min");
    }

    /**
     * 返回 TEXT2SQL 意图决策。
     */
    @Override
    public AgentIntentDecision match(AgentRuntimeContext context) {
        return new AgentIntentDecision(AgentIntent.TEXT2SQL, context.getEffectiveMessage(), null, null, true);
    }
}
