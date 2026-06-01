package cn.mw.loganalysis.dashboard.service;

import cn.mw.loganalysis.dashboard.dto.DashboardDatasetCandidateDTO;
import cn.mw.loganalysis.logcategory.mapper.LogCategoryRegistryMapper;
import cn.mw.loganalysis.vector.entity.ConfigComponent;
import cn.mw.loganalysis.vector.service.ConfigComponentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardDatasetDiscoveryServiceTest {

    @Test
    void shouldResolveTableNameFromAlternateVisualDataKeysAndYaml() {
        ConfigComponentService componentService = mock(ConfigComponentService.class);
        LogCategoryRegistryMapper registryMapper = mock(LogCategoryRegistryMapper.class);
        DashboardDatasetDiscoveryService service = new DashboardDatasetDiscoveryService(
                componentService,
                registryMapper,
                new ObjectMapper()
        );

        ConfigComponent tableNameSink = new ConfigComponent();
        tableNameSink.setId("sink-table-name");
        tableNameSink.setName("regex_logs_sink");
        tableNameSink.setDisplayName("regex_logs");
        tableNameSink.setVectorType("clickhouse");
        tableNameSink.setQueryable(true);
        tableNameSink.setVisualData("{\"database\":\"default\",\"tableName\":\"regex_logs\",\"level\":\"level\"}");

        ConfigComponent yamlOnlySink = new ConfigComponent();
        yamlOnlySink.setId("sink-yaml-only");
        yamlOnlySink.setName("yaml_only_sink");
        yamlOnlySink.setDisplayName("yaml_only_logs");
        yamlOnlySink.setVectorType("clickhouse");
        yamlOnlySink.setQueryable(true);
        yamlOnlySink.setConfigYaml("""
                type: clickhouse
                endpoint: http://localhost:8123
                database: default
                table: yaml_only_logs
                """);

        ConfigComponent templateSink = new ConfigComponent();
        templateSink.setId("template-sink-clickhouse");
        templateSink.setName("ClickHouse Sink Template");
        templateSink.setDisplayName("ClickHouse Logs");
        templateSink.setVectorType("clickhouse");
        templateSink.setQueryable(true);
        templateSink.setIsTemplate(true);
        templateSink.setVisualData("{\"database\":\"default\",\"table\":\"syslog\"}");

        when(componentService.getQueryableClickHouseSinks()).thenReturn(List.of(tableNameSink, yamlOnlySink, templateSink));

        List<DashboardDatasetCandidateDTO> candidates = service.discoverCandidates();

        assertThat(candidates)
                .extracting(DashboardDatasetCandidateDTO::getTableName)
                .containsExactly("regex_logs", "yaml_only_logs");

        assertThat(candidates.get(0).getFieldMapping())
                .containsEntry("timestamp", "timestamp")
                .containsEntry("severity", "level")
                .containsEntry("message", "message");
    }
}
