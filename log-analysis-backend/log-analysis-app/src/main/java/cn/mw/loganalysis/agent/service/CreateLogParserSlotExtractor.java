package cn.mw.loganalysis.agent.service;

import cn.mw.loganalysis.vector.entity.ConfigComponent;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * CREATE_LOG_PARSER 的槽位处理链入口。
 *
 * 类似参考工程的 SlotChainManager：本类只负责按顺序调度 SlotHandler，
 * 不再承载具体字段或 Source 类型的抽取细节。
 */
@Component
public class CreateLogParserSlotExtractor {

    private final List<CreateLogParserSlotHandler> slotHandlers;

    /**
     * 收集所有槽位处理器并按 Ordered 顺序排序。
     */
    public CreateLogParserSlotExtractor(List<CreateLogParserSlotHandler> slotHandlers) {
        this.slotHandlers = new ArrayList<>(slotHandlers);
        AnnotationAwareOrderComparator.sort(this.slotHandlers);
    }

    /**
     * 依次执行槽位处理器，把用户文本合并进任务帧。
     */
    void mergeSlots(AgentTaskFrame frame, String rawMessage, ConfigComponent currentSink) {
        CreateLogParserSlotContext context = new CreateLogParserSlotContext(
                frame,
                AgentToolSupport.normalizeText(rawMessage),
                currentSink
        );
        for (CreateLogParserSlotHandler handler : slotHandlers) {
            if (handler.supports(context)) {
                handler.fill(context);
            }
        }
    }

    /**
     * 判断当前消息是否像是在补齐创建日志解析所需的槽位。
     */
    boolean looksLikeSlotFilling(String message) {
        String lower = StringUtils.lowerCase(AgentToolSupport.normalizeText(message), Locale.ROOT);
        if (StringUtils.isBlank(lower)) {
            return false;
        }
        if (AgentToolSupport.containsAny(lower, "查询", "查看", "搜索", "最近")
                && !AgentToolSupport.containsAny(lower, "表名", "来源", "source", "自动生成", "路径", "地址", "端口", "topic", "bootstrap")) {
            return false;
        }
        return AgentToolSupport.containsAny(lower,
                "表名", "tablename", "目标表", "写入表", "来源", "source", "当前数据源",
                "clickhouse", "sink", "文件", "file", "syslog", "socket", "kafka", "topic",
                "bootstrap", "路径", "path", "地址", "address", "监听", "端口", "port",
                "已有 source", "已有source", "自动生成", "存", "写入", "入库");
    }
}
