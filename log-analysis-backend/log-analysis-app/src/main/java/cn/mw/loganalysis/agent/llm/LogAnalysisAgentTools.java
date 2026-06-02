package cn.mw.loganalysis.agent.llm;

import cn.mw.loganalysis.agent.tool.AgentToolFacade;
import cn.mw.loganalysis.agent.tool.AgentToolPayload;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 日志智能助手 Tools。
 *
 * 这里保留 LangChain4j 所需的 @Tool 壳子，
 * 真正的业务实现统一下沉到 AgentToolFacade。
 */
@Component
@RequiredArgsConstructor
public class LogAnalysisAgentTools {

    private final AgentToolFacade agentToolFacade;

    @Tool(name = "get_schema", value = "读取当前数据源的字段结构、时间字段、统计维度和内容字段")
    public AgentToolPayload getSchema() {
        return agentToolFacade.getSchema();
    }

    @Tool(name = "query_logs", value = "查询当前数据源的日志列表。timeRange 传自然语言时间范围，例如 最近1小时、最近24小时、今天、昨天；keyword 可为空；severity 可传 error/warn/info/debug；limit 为返回条数上限")
    public AgentToolPayload queryLogs(@P("自然语言时间范围，例如 最近1小时、最近24小时、今天、昨天。为空时默认最近1小时") String timeRange,
                                      @P("日志关键词，可为空") String keyword,
                                      @P("日志级别，可为空，可传 error、warn、info、debug") String severity,
                                      @P("返回日志条数上限，建议 10 到 50") Integer limit) {
        return agentToolFacade.queryLogs(timeRange, keyword, severity, limit);
    }

    @Tool(name = "query_timeseries", value = "查询当前数据源的日志趋势。timeRange 传自然语言时间范围，例如 最近24小时、最近7天；granularity 可传 1m、5m、1h、1d")
    public AgentToolPayload queryTimeseries(@P("自然语言时间范围，例如 最近24小时、最近7天、今天。为空时默认最近24小时") String timeRange,
                                            @P("趋势粒度，可传 1m、5m、1h、1d，或留空自动选择") String granularity) {
        return agentToolFacade.queryTimeseries(timeRange, granularity);
    }

    @Tool(name = "text2sql_query", value = "当当前数据源是 ClickHouse，且用户要做开放式统计、聚合、排行、按字段分组、多少条、临时做图时，使用自然语言生成 SQL 并执行。query 直接传用户原始问题")
    public AgentToolPayload text2SqlQuery(@P("面向当前 ClickHouse 数据源的自然语言查询原文，例如 最近1天的数据有多少条、按 severity 统计最近24小时数量") String query) {
        return agentToolFacade.text2SqlQuery(query);
    }

    @Tool(name = "preview_vector_components", value = "根据用户提供的一条日志样本预览生成 Vector Remap Transform 和 ClickHouse Sink 组件计划。只生成预览，不建表、不写组件；用户点击确认创建后才会落库。")
    public AgentToolPayload previewVectorComponents(@P("一条原始日志样本，尽量只传日志本身，不要传解释文字") String logSample,
                                                    @P("目标 ClickHouse 数据源 ID，可为空；为空时使用当前会话选择的 ClickHouse Sink 关联的数据源") String datasourceId,
                                                    @P("希望创建的 ClickHouse 表名，可为空；为空时后端自动生成") String tableName,
                                                    @P("命名捕获正则，建议使用 (?P<field>...) 捕获字段；为空时后端按日志样本启发式生成并校验") String regexPattern) {
        return agentToolFacade.previewVectorComponents(logSample, datasourceId, tableName, regexPattern);
    }
}
