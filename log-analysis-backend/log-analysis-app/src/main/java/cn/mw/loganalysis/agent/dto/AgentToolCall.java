package cn.mw.loganalysis.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 智能助手工具调用记录
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentToolCall {

    /**
     * 工具编码
     */
    private String toolName;

    /**
     * 工具展示名称
     */
    private String toolLabel;

    /**
     * 调用状态
     */
    private String status;

    /**
     * 本次调用的输入参数摘要
     */
    private Map<String, Object> input;

    /**
     * 本次调用结果摘要
     */
    private String summary;

    /**
     * 调用耗时
     */
    private Long durationMs;
}
