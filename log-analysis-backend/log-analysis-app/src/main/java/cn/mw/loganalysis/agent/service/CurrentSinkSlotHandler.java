package cn.mw.loganalysis.agent.service;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class CurrentSinkSlotHandler implements CreateLogParserSlotHandler {

    /**
     * 最后从当前选中的 Sink 中补齐入库目标。
     */
    @Override
    public int getOrder() {
        return 100;
    }

    /**
     * 只有当前 Sink 是 ClickHouse 时才能作为创建目标。
     */
    @Override
    public boolean supports(CreateLogParserSlotContext context) {
        return context.currentSink() != null
                && StringUtils.equalsIgnoreCase(context.currentSink().getVectorType(), "clickhouse");
    }

    /**
     * 将当前 ClickHouse Sink 和关联数据源写入任务帧。
     */
    @Override
    public void fill(CreateLogParserSlotContext context) {
        context.frame().setTargetSinkId(context.currentSink().getId());
        context.frame().setTargetDatasourceId(context.currentSink().getDatasourceId());
        context.frame().setDatasourceName(StringUtils.defaultIfBlank(
                context.currentSink().getDisplayName(),
                context.currentSink().getName()
        ));
    }
}
