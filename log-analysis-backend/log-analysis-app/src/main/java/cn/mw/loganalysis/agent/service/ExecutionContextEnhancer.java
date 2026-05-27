package cn.mw.loganalysis.agent.service;

import org.springframework.stereotype.Component;

/**
 * 构建工具执行上下文。
 */
@Component
public class ExecutionContextEnhancer implements AgentContextEnhancer {

    /**
     * 指定工具执行上下文在基础信息加载完成后构建。
     */
    @Override
    public int getOrder() {
        return 30;
    }

    /**
     * 将运行时上下文转换为工具层使用的 AgentExecutionContext。
     */
    @Override
    public void enhance(AgentRuntimeContext context) {
        context.setExecutionContext(new AgentExecutionContext(
                context.getDatasource() != null ? context.getRequest().getDatasourceId() : null,
                context.getDatasource() != null ? context.getDatasource().getName() : null,
                context.getDatasource() != null ? context.getDatasource().getVectorType() : null,
                context.getUserId(),
                context.getSessionId()
        ));
    }
}
