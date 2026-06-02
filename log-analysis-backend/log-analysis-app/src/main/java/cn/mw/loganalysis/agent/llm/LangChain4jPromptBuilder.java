package cn.mw.loganalysis.agent.llm;

import cn.mw.loganalysis.agent.dto.AgentChatMessage;
import cn.mw.loganalysis.agent.dto.AgentChatRequest;
import cn.mw.loganalysis.vector.entity.ConfigComponent;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 构造 LangChain4j 系统提示词和用户上下文提示词。
 */
@Component
public class LangChain4jPromptBuilder {

    private static final String SYSTEM_PROMPT = """
            你是企业日志分析平台里的智能助手。
            你的职责只有五类：
            1. 读取当前数据源的字段结构
            2. 查询当前数据源的日志列表
            3. 查询当前数据源的日志趋势
            4. 对 ClickHouse 数据源执行自然语言 SQL 查询
            5. 根据用户提供的日志样本预览生成 Vector Remap/Sink 组件创建计划

            你必须遵守以下规则：
            - 所有结论都必须基于工具返回的数据，不允许编造字段、数量、时间范围或日志内容。
            - 回答字段、表结构、有哪些列、时间字段、统计维度时，先调用 get_schema。
            - 回答日志明细、错误日志、搜索关键词、最近多少时间的日志时，先调用 query_logs。
            - 回答趋势、时序、波动、按分钟/小时统计时，先调用 query_timeseries。
            - 当问题属于开放式统计、聚合、排行、按字段分组、多少条、做图、生成报表数据，并且当前数据源类型是 clickhouse 时，优先调用 text2sql_query。
            - text2sql_query 已经会把自然语言、当前表结构和数据源信息交给既有 text2sql 服务处理，你不能自行编造 SQL。
            - 如果当前数据源不是 clickhouse，不要调用 text2sql_query。
            - 当用户要求根据日志样本创建、生成、配置 Vector 组件、Remap/Sink、正则或入库表时，先调用 preview_vector_components。
            - preview_vector_components 只生成预览计划，不会建表或写入组件；必须提醒用户检查后点击“确认创建”，不要声称已经创建成功。
            - 调用 preview_vector_components 时，尽量提供命名捕获正则 regexPattern，例如 (?P<field>...)；如果无法可靠生成，可以留空让后端启发式生成并校验。
            - 默认一次问题只调用一个最合适的工具；只有确实必要时才继续调用第二个工具。
            - 回答使用简体中文，保持简洁，先给结论，再点出关键数字或时间点。
            - 如果用户的问题超出这五类能力，明确说明当前只支持字段结构、日志查询、趋势查询、ClickHouse 自然语言统计查询和 Vector 组件预览生成。
            """;

    String systemPrompt() {
        return SYSTEM_PROMPT;
    }

    String buildUserPrompt(AgentChatRequest request, ConfigComponent datasource) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("当前数据源名称: ").append(datasource.getName()).append('\n');
        prompt.append("当前数据源类型: ").append(datasource.getVectorType()).append('\n');
        prompt.append("当前问题: ").append(normalizeText(request.getMessage())).append('\n');

        List<AgentChatMessage> history = request.getHistory();
        if (history != null && !history.isEmpty()) {
            prompt.append("最近对话历史:\n");
            for (AgentChatMessage message : history) {
                if (message == null || StringUtils.isBlank(message.getContent())) {
                    continue;
                }
                prompt.append("- ")
                        .append(normalizeRole(message.getRole()))
                        .append(": ")
                        .append(normalizeText(message.getContent()))
                        .append('\n');
            }
        }

        prompt.append("请先使用合适的工具，再基于工具结果回答。");
        return prompt.toString();
    }

    private String normalizeRole(String role) {
        return "assistant".equalsIgnoreCase(role) ? "assistant" : "user";
    }

    private String normalizeText(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }
}
