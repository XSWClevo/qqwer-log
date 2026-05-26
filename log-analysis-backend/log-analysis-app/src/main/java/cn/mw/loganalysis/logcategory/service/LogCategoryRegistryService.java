package cn.mw.loganalysis.logcategory.service;

import cn.mw.loganalysis.logcategory.entity.LogCategoryRegistry;
import cn.mw.loganalysis.logcategory.mapper.LogCategoryRegistryMapper;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * 日志分类注册表服务
 */
@Service
@RequiredArgsConstructor
public class LogCategoryRegistryService {

    private final LogCategoryRegistryMapper logCategoryRegistryMapper;

    public List<LogCategoryRegistry> listEnabled() {
        return logCategoryRegistryMapper.selectEnabled();
    }

    public List<LogCategoryRegistry> listEnabledByCategoryCodes(List<String> categoryCodes) {
        if (CollectionUtils.isEmpty(categoryCodes)) {
            return Collections.emptyList();
        }
        return logCategoryRegistryMapper.selectEnabledByCategoryCodes(categoryCodes);
    }

    public List<LogCategoryRegistry> listEnabledByDatasourceIds(List<String> datasourceIds) {
        if (CollectionUtils.isEmpty(datasourceIds)) {
            return Collections.emptyList();
        }
        return logCategoryRegistryMapper.selectEnabledByDatasourceIds(datasourceIds);
    }

    public List<LogCategoryRegistry> listEnabledByTableNames(List<String> tableNames) {
        if (CollectionUtils.isEmpty(tableNames)) {
            return Collections.emptyList();
        }
        return logCategoryRegistryMapper.selectEnabledByTableNames(tableNames);
    }

    public LogCategoryRegistry getEnabledByDatasourceAndTable(String datasourceId, String tableName) {
        return logCategoryRegistryMapper.selectEnabledByDatasourceAndTable(datasourceId, tableName);
    }
}
