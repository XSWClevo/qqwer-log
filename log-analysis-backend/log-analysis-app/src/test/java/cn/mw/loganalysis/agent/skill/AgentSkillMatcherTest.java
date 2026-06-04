package cn.mw.loganalysis.agent.skill;

import cn.mw.loganalysis.agent.dto.AgentChatRequest;
import cn.mw.loganalysis.agent.execution.AgentRuntimeContext;
import cn.mw.loganalysis.agent.nlu.AgentIntent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentSkillMatcherTest {

    private final AgentSkillMatcher matcher = new AgentSkillMatcher(new AgentSkillRegistry());

    @Test
    void match_should_map_component_library_configuration_to_create_log_parser_when_page_context_is_present() {
        AgentSkillDecision decision = matcher.match(context("帮我创建组件库配置", "COMPONENT_LIBRARY", null));

        assertThat(decision.hasIntentDecision()).isTrue();
        assertThat(decision.intentDecision().intent()).isEqualTo(AgentIntent.CREATE_LOG_PARSER);
        assertThat(decision.intentDecision().deterministicToolRequest()).isTrue();
    }

    @Test
    void match_should_ask_clarification_for_component_library_configuration_without_page_context() {
        AgentSkillDecision decision = matcher.match(context("帮我创建组件库配置", null, null));

        assertThat(decision.requiresClarification()).isTrue();
        assertThat(decision.hasIntentDecision()).isFalse();
        assertThat(decision.clarificationMessage()).contains("组件库").contains("日志样本");
    }

    @Test
    void match_should_map_log_sample_component_generation_to_vector_component_plan() {
        AgentSkillDecision decision = matcher.match(context("根据这条日志样本生成组件", null, null));

        assertThat(decision.hasIntentDecision()).isTrue();
        assertThat(decision.intentDecision().intent()).isEqualTo(AgentIntent.VECTOR_COMPONENT_PLAN);
    }

    @Test
    void match_should_not_map_bare_create_component_without_context() {
        AgentSkillDecision decision = matcher.match(context("创建组件", null, null));

        assertThat(decision.requiresClarification()).isTrue();
        assertThat(decision.hasIntentDecision()).isFalse();
    }

    private AgentRuntimeContext context(String message, String pageContext, String skillId) {
        AgentChatRequest request = new AgentChatRequest();
        request.setMessage(message);
        request.setPageContext(pageContext);
        request.setSkillId(skillId);
        return AgentRuntimeContext.builder()
                .request(request)
                .normalizedMessage(message)
                .effectiveMessage(message)
                .pageContext(pageContext)
                .skillId(skillId)
                .build();
    }
}
