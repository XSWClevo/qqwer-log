package cn.mw.loganalysis.agent.service;

import cn.mw.loganalysis.agent.dto.AgentResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tool 返回给大模型和编排层的载荷
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentToolPayload {

    private String toolName;

    private String toolLabel;

    private String intent;

    private String summary;

    private Long durationMs;

    private AgentResult result;
}
