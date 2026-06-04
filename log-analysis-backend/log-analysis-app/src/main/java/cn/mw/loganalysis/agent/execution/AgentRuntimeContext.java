package cn.mw.loganalysis.agent.execution;

import cn.mw.loganalysis.agent.dto.AgentChatRequest;
import cn.mw.loganalysis.agent.dto.AgentChatResponse;
import cn.mw.loganalysis.agent.nlu.AgentIntent;
import cn.mw.loganalysis.agent.nlu.AgentNluSlots;
import cn.mw.loganalysis.agent.skill.AgentSkillDecision;
import cn.mw.loganalysis.agent.support.AgentStreamEventEmitter;
import cn.mw.loganalysis.agent.tool.AgentToolPayload;
import cn.mw.loganalysis.agent.nlu.IntentNode;
import cn.mw.loganalysis.agent.nlu.IntentSlotsEntity;
import cn.mw.loganalysis.vector.entity.ConfigComponent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

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
public class AgentRuntimeContext {

    private AgentChatRequest request;

    private String skillId;

    private String pageContext;

    private String routePath;

    private Map<String, Object> surfaceContext;

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

    private AgentSkillDecision skillDecision;

    private AgentExecutionContext executionContext;

    private AgentToolPayload toolPayload;

    private AgentChatResponse response;

    /**
     * 获取模型 NLU 建议的通用槽位。
     */
    public AgentNluSlots getNluSlots() {
        if (intentNode == null || !(intentNode.getSlots() instanceof AgentNluSlots slots)) {
            return null;
        }
        return slots;
    }
}
