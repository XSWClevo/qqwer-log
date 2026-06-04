package cn.mw.loganalysis.agent.conversation;

import cn.mw.loganalysis.agent.dto.AgentChatRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentConversationMemoryServiceTest {

    @Test
    void prepare_should_preserve_page_context_route_path_and_skill_id() {
        AgentConversationMemoryService service = new AgentConversationMemoryService(2048, "gpt-5.2");

        AgentChatRequest request = new AgentChatRequest();
        request.setMessage("帮我创建解析组件");
        request.setSessionId("session-1");
        request.setDatasourceId("datasource-1");
        request.setPageContext("COMPONENT_LIBRARY");
        request.setRoutePath("/vector/components");
        request.setSkillId("create_log_parser_from_component_library");

        AgentConversationMemoryService.PreparedAgentChatRequest prepared = service.prepare(request, 1001L);

        assertThat(prepared.request().getPageContext()).isEqualTo("COMPONENT_LIBRARY");
        assertThat(prepared.request().getRoutePath()).isEqualTo("/vector/components");
        assertThat(prepared.request().getSkillId()).isEqualTo("create_log_parser_from_component_library");
    }
}
