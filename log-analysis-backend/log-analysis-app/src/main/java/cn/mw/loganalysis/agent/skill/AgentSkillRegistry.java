package cn.mw.loganalysis.agent.skill;

import cn.mw.loganalysis.agent.nlu.AgentIntent;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 内置技能注册表。
 */
@Component
public class AgentSkillRegistry {

    private final List<AgentSkillDefinition> definitions = List.of(
            new AgentSkillDefinition(
                    "create_log_parser_component",
                    "创建日志解析组件",
                    Set.of("create_log_parser_from_component_library"),
                    List.of("组件库配置", "创建日志解析", "生成解析组件", "日志解析组件", "生成正则", "入库", "建表", "采集日志"),
                    List.of("日志", "解析", "样本", "正则", "入库", "建表", "采集"),
                    List.of("前端组件", "页面组件", "vue", "element"),
                    Set.of("COMPONENT_LIBRARY", "LOG_PARSER_WIZARD"),
                    AgentIntent.CREATE_LOG_PARSER,
                    true,
                    "我理解你想在组件库里创建日志解析组件。请补充一条日志样本，或说明日志来源、目标表名和入库数据源。"
            ),
            new AgentSkillDefinition(
                    "preview_vector_component_plan",
                    "预览组件配置",
                    Set.of(),
                    List.of("生成组件", "生成组件预览", "预览组件", "组件预览", "vector组件", "vector 组件", "remap", "sink"),
                    List.of("日志样本", "样本", "vector", "remap", "sink"),
                    List.of("前端组件", "页面组件", "vue", "element"),
                    Set.of("COMPONENT_LIBRARY", "VECTOR_EDITOR", "AGENT_CHAT"),
                    AgentIntent.VECTOR_COMPONENT_PLAN,
                    true,
                    "我可以先生成组件配置预览。请粘贴日志样本，并说明目标数据源或表名。"
            )
    );

    /**
     * 返回当前注册的全部内置技能。
     */
    public List<AgentSkillDefinition> definitions() {
        return definitions;
    }
}
