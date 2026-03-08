package cn.mw.loganalysis.agent.service;

/**
 * 当前请求的智能助手执行上下文
 *
 * @param datasourceId   数据源ID
 * @param datasourceName 数据源名称
 * @param datasourceType 数据源类型，例如 clickhouse / postgresql / elasticsearch
 */
record AgentExecutionContext(String datasourceId, String datasourceName, String datasourceType) {
}
