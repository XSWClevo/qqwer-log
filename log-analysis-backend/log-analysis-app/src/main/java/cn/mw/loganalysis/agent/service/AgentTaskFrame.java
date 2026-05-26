package cn.mw.loganalysis.agent.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 智能助手会话内的任务帧。
 *
 * 用于承接“创建日志解析”这类需要多轮补槽的配置任务，
 * 避免第二轮用户只补表名/来源时丢失第一轮日志样本。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class AgentTaskFrame {

    private String taskId;

    private Long userId;

    private String sessionId;

    private AgentIntent intent;

    private AgentTaskStatus status;

    private String logSample;

    private String targetSinkId;

    private String targetDatasourceId;

    private String datasourceName;

    private String tableName;

    private String componentPrefix;

    private String sourceType;

    private Map<String, Object> sourceConfig;

    private String regexPattern;

    private String parseMethod;

    private Boolean confirmCommit;

    private List<String> missingSlots;

    private String nextAction;

    private LocalDateTime updatedAt;
}
