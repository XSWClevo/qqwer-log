package cn.mw.loganalysis.agent.execution;

/**
 * 当前请求的智能助手执行上下文
 *
 * @param datasourceId   当前前端选择的可查询 Sink 组件ID
 * @param datasourceName 数据源展示名称
 * @param datasourceType 数据源类型，例如 clickhouse / postgresql / elasticsearch
 * @param userId         当前登录用户ID
 * @param sessionId      当前智能助手会话ID
 */
public record AgentExecutionContext(String datasourceId,
                             String datasourceName,
                             String datasourceType,
                             Long userId,
                             String sessionId) {
}
