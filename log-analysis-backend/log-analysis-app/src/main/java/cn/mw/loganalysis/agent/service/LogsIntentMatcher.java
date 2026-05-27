package cn.mw.loganalysis.agent.service;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class LogsIntentMatcher implements AgentIntentMatcher {

    /**
     * 作为兜底意图，最后匹配。
     */
    @Override
    public int getOrder() {
        return 1000;
    }

    /**
     * 日志查询是兜底策略，始终可匹配。
     */
    @Override
    public boolean matches(AgentRuntimeContext context) {
        return true;
    }

    /**
     * 返回 LOGS 意图，并顺带提取关键词和日志级别。
     */
    @Override
    public AgentIntentDecision match(AgentRuntimeContext context) {
        String message = context.getEffectiveMessage();
        return new AgentIntentDecision(
                AgentIntent.LOGS,
                message,
                AgentIntentTextSupport.extractKeyword(message),
                AgentIntentTextSupport.extractSeverity(message),
                isDeterministicLogRequest(message, context.getDatasource() != null ? context.getDatasource().getVectorType() : null)
        );
    }

    /**
     * 判断普通日志查询是否足够确定，可以跳过 LLM。
     */
    private boolean isDeterministicLogRequest(String message, String datasourceType) {
        String lower = StringUtils.lowerCase(message, Locale.ROOT);
        return AgentToolSupport.containsAny(lower,
                "日志", "log", "查询", "查看", "搜索", "查找", "包含", "关键字", "关键词",
                "最近", "今天", "昨天", "错误", "异常", "告警", "警告", "warn", "warning",
                "error", "info", "debug", "trace", "fatal", "critical", "message", "severity",
                "source", "host", "hostname", "ip", "路径", "接口", "状态码", "status")
                || StringUtils.isNotBlank(AgentIntentTextSupport.extractSeverity(message))
                || StringUtils.isBlank(datasourceType);
    }
}
