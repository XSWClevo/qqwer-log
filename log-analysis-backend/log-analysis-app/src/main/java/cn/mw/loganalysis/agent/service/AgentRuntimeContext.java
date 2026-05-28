package cn.mw.loganalysis.agent.service;

import cn.mw.loganalysis.agent.dto.AgentChatRequest;
import cn.mw.loganalysis.agent.dto.AgentChatResponse;
import cn.mw.loganalysis.vector.entity.ConfigComponent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 规则 Agent 的一次请求上下文。
 *
 * 参考导航 Agent 的 RequestStaticInfoDto / StrategyContext：所有节点只读写上下文，
 * 避免在方法参数之间反复传递一长串零散状态。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class AgentRuntimeContext {

    private AgentChatRequest request;

    private Long userId;

    private String sessionId;

    private AgentStreamEventEmitter emitter;

    private ConfigComponent datasource;

    private String normalizedMessage;

    private String effectiveMessage;

    private AgentIntent intent;

    private List<IntentSlotsEntity> intentSlots;

    private IntentNode intentNode;

    private String keyword;

    private String severity;

    private boolean deterministicToolRequest;

    private AgentExecutionContext executionContext;

    private AgentToolPayload toolPayload;

    private AgentChatResponse response;

    /**
     * 获取模型 NLU 建议的通用槽位。
     */
    AgentNluSlots getNluSlots() {
        if (intentNode == null || !(intentNode.getSlots() instanceof AgentNluSlots slots)) {
            return null;
        }
        return slots;
    }
}
