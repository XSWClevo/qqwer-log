package cn.mw.loganalysis.attack.reader;

import cn.mw.loganalysis.attack.entity.AttackLogDataset;
import cn.mw.loganalysis.common.enums.DatasourceType;
import cn.mw.loganalysis.datasource.entity.Datasource;
import cn.mw.loganalysis.datasource.repository.DatasourceRepository;
import cn.mw.loganalysis.stats.service.DynamicLogQueryService;
import cn.mw.loganalysis.stats.service.query.DatasourceConnectionConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Resolves the physical datasource used by an attack-classification dataset.
 * datasourceId can point to either a queryable Vector sink component or a managed datasource.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttackDatasourceConfigResolver {

    private final DynamicLogQueryService dynamicLogQueryService;
    private final DatasourceRepository datasourceRepository;
    private final Environment environment;

    public DatasourceConnectionConfig resolve(AttackLogDataset dataset) {
        if (ObjectUtils.isEmpty(dataset)) {
            throw new IllegalArgumentException("攻击分类数据集不能为空");
        }

        String datasourceType = StringUtils.lowerCase(StringUtils.trim(dataset.getDatasourceType()));
        String datasourceId = StringUtils.trimToEmpty(dataset.getDatasourceId());
        DatasourceConnectionConfig baseConfig = StringUtils.isBlank(datasourceId)
                ? defaultApplicationConfig(datasourceType)
                : resolveByDatasourceId(datasourceId);

        if (StringUtils.isNotBlank(datasourceType)
                && StringUtils.isNotBlank(baseConfig.getType())
                && !StringUtils.equalsIgnoreCase(datasourceType, baseConfig.getType())) {
            throw new IllegalArgumentException("攻击分类数据集数据源类型不一致: datasetType="
                    + datasourceType + ", datasourceType=" + baseConfig.getType());
        }

        return overrideDatasetTarget(baseConfig, dataset, datasourceType);
    }

    private DatasourceConnectionConfig resolveByDatasourceId(String datasourceId) {
        RuntimeException vectorConfigError = null;
        try {
            return dynamicLogQueryService.getDatasourceConfigPublic(datasourceId);
        } catch (RuntimeException ex) {
            vectorConfigError = ex;
            log.debug("datasourceId={} 不是可查询 Vector Sink 或解析失败: {}", datasourceId, ex.getMessage());
        }

        Datasource datasource = datasourceRepository.findById(datasourceId);
        if (ObjectUtils.isNotEmpty(datasource)) {
            return fromManagedDatasource(datasource);
        }

        String message = "无法解析攻击分类数据源: " + datasourceId;
        if (ObjectUtils.isNotEmpty(vectorConfigError) && StringUtils.isNotBlank(vectorConfigError.getMessage())) {
            message += "，" + vectorConfigError.getMessage();
        }
        throw new IllegalArgumentException(message);
    }

    private DatasourceConnectionConfig fromManagedDatasource(Datasource datasource) {
        DatasourceType datasourceType = DatasourceType.fromCode(datasource.getType());
        String endpoint = datasourceType.isVersionProbeSupported()
                ? datasourceType.buildJdbcUrl(datasource)
                : buildHttpEndpoint(datasource);

        return DatasourceConnectionConfig.builder()
                .type(datasourceType.getCode())
                .endpoint(endpoint)
                .database(StringUtils.defaultIfBlank(datasource.getDatabaseName(), datasourceType.getDefaultDatabaseName()))
                .username(datasource.getUsername())
                .password(datasource.getPassword())
                .tls(datasource.getSslEnabled())
                .extraConfig(datasource.getConnectionParams())
                .componentId(datasource.getId())
                .componentName(datasource.getName())
                .build();
    }

    private String buildHttpEndpoint(Datasource datasource) {
        String protocol = BooleanUtils.isTrue(datasource.getSslEnabled()) ? "https" : "http";
        return protocol + "://" + datasource.getHost() + ":" + datasource.getPort();
    }

    private DatasourceConnectionConfig defaultApplicationConfig(String datasourceType) {
        if (StringUtils.equalsIgnoreCase(datasourceType, "clickhouse")) {
            return DatasourceConnectionConfig.builder()
                    .type("clickhouse")
                    .endpoint(environment.getProperty("spring.datasource.dynamic.datasource.clickhouse.url"))
                    .username(environment.getProperty("spring.datasource.dynamic.datasource.clickhouse.username"))
                    .password(environment.getProperty("spring.datasource.dynamic.datasource.clickhouse.password"))
                    .database(environment.getProperty("CLICKHOUSE_DB", "default"))
                    .build();
        }

        return DatasourceConnectionConfig.builder()
                .type(StringUtils.defaultIfBlank(datasourceType, "clickhouse"))
                .build();
    }

    private DatasourceConnectionConfig overrideDatasetTarget(DatasourceConnectionConfig baseConfig,
                                                            AttackLogDataset dataset,
                                                            String datasourceType) {
        String resolvedType = StringUtils.defaultIfBlank(datasourceType, baseConfig.getType());
        String targetName = resolveTargetName(dataset, baseConfig, resolvedType);

        return DatasourceConnectionConfig.builder()
                .type(resolvedType)
                .endpoint(baseConfig.getEndpoint())
                .database(StringUtils.defaultIfBlank(dataset.getDatabaseName(), baseConfig.getDatabase()))
                .table(targetName)
                .username(baseConfig.getUsername())
                .password(baseConfig.getPassword())
                .tls(baseConfig.getTls())
                .extraConfig(baseConfig.getExtraConfig())
                .rawYaml(baseConfig.getRawYaml())
                .componentId(baseConfig.getComponentId())
                .componentName(baseConfig.getComponentName())
                .build();
    }

    private String resolveTargetName(AttackLogDataset dataset,
                                     DatasourceConnectionConfig baseConfig,
                                     String datasourceType) {
        if (StringUtils.equalsIgnoreCase(datasourceType, "elasticsearch")) {
            return StringUtils.defaultIfBlank(dataset.getIndexName(),
                    StringUtils.defaultIfBlank(dataset.getTableName(), baseConfig.getTable()));
        }
        return StringUtils.defaultIfBlank(dataset.getTableName(),
                StringUtils.defaultIfBlank(dataset.getIndexName(), baseConfig.getTable()));
    }
}
