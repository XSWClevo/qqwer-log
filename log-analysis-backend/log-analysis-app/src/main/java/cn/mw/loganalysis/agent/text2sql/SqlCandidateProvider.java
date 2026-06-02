package cn.mw.loganalysis.agent.text2sql;

import cn.mw.loganalysis.agent.execution.AgentExecutionContext;
import org.springframework.core.Ordered;

import java.util.Optional;

/**
 * Text2SQL 候选来源策略。
 */
public interface SqlCandidateProvider extends Ordered {

    /**
     * 候选来源标识，例如 template / history / llm。
     */
    String source();

    /**
     * 判断当前查询是否适合由该候选来源处理。
     */
    boolean supports(AgentExecutionContext context, String query);

    /**
     * 生成候选 SQL；不负责校验和执行。
     */
    Optional<SqlCandidate> generate(AgentExecutionContext context, String query);

    /**
     * 返回候选来源优先级，数值越小越先调度。
     */
    @Override
    default int getOrder() {
        return 0;
    }
}
