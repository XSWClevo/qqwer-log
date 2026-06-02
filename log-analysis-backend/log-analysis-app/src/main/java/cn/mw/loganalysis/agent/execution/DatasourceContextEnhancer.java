package cn.mw.loganalysis.agent.execution;

import cn.mw.loganalysis.vector.entity.ConfigComponent;
import cn.mw.loganalysis.vector.service.ConfigComponentService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * 加载当前选中的可查询 Vector Sink。
 */
@Component
@RequiredArgsConstructor
public class DatasourceContextEnhancer implements AgentContextEnhancer {

    private final ConfigComponentService configComponentService;

    /**
     * 指定数据源加载在消息增强之后执行。
     */
    @Override
    public int getOrder() {
        return 20;
    }

    /**
     * 根据请求 datasourceId 加载当前可查询 Sink。
     */
    @Override
    public void enhance(AgentRuntimeContext context) {
        if (StringUtils.isBlank(context.getRequest().getDatasourceId())) {
            return;
        }
        ConfigComponent datasource = configComponentService.getQueryableDataSourceById(context.getRequest().getDatasourceId());
        context.setDatasource(datasource);
    }
}
