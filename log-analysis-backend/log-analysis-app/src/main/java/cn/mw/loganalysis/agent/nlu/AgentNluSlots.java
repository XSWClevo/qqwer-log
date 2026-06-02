package cn.mw.loganalysis.agent.nlu;

import cn.mw.loganalysis.agent.support.SlotResult;
import lombok.Data;

import java.util.Map;

/**
 * 模型 NLU 建议槽位。
 *
 * v1 使用通用槽位对象承接日志查询、趋势查询和创建日志解析。
 * 后续某个意图复杂度升高时，再拆成独立 SlotResult 子类。
 */
@Data
public class AgentNluSlots implements SlotResult {

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

    private String keyword;

    private String severity;

    private String timeRange;

    private String granularity;

    private String query;
}
