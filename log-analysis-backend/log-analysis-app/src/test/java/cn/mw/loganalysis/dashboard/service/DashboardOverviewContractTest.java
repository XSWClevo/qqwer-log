package cn.mw.loganalysis.dashboard.service;

import cn.mw.loganalysis.dashboard.dto.DashboardCapabilityDTO;
import cn.mw.loganalysis.dashboard.dto.DashboardLogKpisDTO;
import cn.mw.loganalysis.dashboard.dto.DashboardMetricDrilldownDTO;
import cn.mw.loganalysis.dashboard.dto.DashboardOverviewDTO;
import cn.mw.loganalysis.dashboard.dto.DashboardStorageVolumeDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardOverviewContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldExposeCapabilitiesAndMetricDrilldownsOnOverview() throws Exception {
        DashboardOverviewDTO overview = DashboardOverviewDTO.builder()
                .capabilities(List.of(
                        DashboardCapabilityDTO.builder()
                                .key("host_topn")
                                .supported(true)
                                .reason("hostname field is available")
                                .fallbackView("message_rank")
                                .build()
                ))
                .metricDrilldowns(List.of(
                        DashboardMetricDrilldownDTO.builder()
                                .metricKey("errorCount")
                                .title("Error Trend")
                                .description("Shows error count changes over time")
                                .unit("count")
                                .build()
                ))
                .build();

        JsonNode json = objectMapper.valueToTree(overview);

        assertThat(json.has("capabilities")).isTrue();
        assertThat(json.has("metricDrilldowns")).isTrue();
        assertThat(json.path("capabilities")).hasSize(1);
        assertThat(json.path("metricDrilldowns")).hasSize(1);
        assertThat(json.path("capabilities").get(0).has("key")).isTrue();
        assertThat(json.path("capabilities").get(0).has("supported")).isTrue();
        assertThat(json.path("capabilities").get(0).has("reason")).isTrue();
        assertThat(json.path("capabilities").get(0).has("fallbackView")).isTrue();
        assertThat(json.path("capabilities").get(0).path("key").asText()).isEqualTo("host_topn");
        assertThat(json.path("capabilities").get(0).path("supported").asBoolean()).isTrue();
        assertThat(json.path("capabilities").get(0).path("reason").asText()).isEqualTo("hostname field is available");
        assertThat(json.path("capabilities").get(0).path("fallbackView").asText()).isEqualTo("message_rank");
        assertThat(json.path("metricDrilldowns").get(0).has("metricKey")).isTrue();
        assertThat(json.path("metricDrilldowns").get(0).has("title")).isTrue();
        assertThat(json.path("metricDrilldowns").get(0).has("description")).isTrue();
        assertThat(json.path("metricDrilldowns").get(0).has("unit")).isTrue();
        assertThat(json.path("metricDrilldowns").get(0).path("metricKey").asText()).isEqualTo("errorCount");
        assertThat(json.path("metricDrilldowns").get(0).path("title").asText()).isEqualTo("Error Trend");
        assertThat(json.path("metricDrilldowns").get(0).path("description").asText()).isEqualTo("Shows error count changes over time");
        assertThat(json.path("metricDrilldowns").get(0).path("unit").asText()).isEqualTo("count");
    }

    @Test
    void shouldExposeStorageVolumeInsideLogKpis() throws Exception {
        DashboardLogKpisDTO logKpis = DashboardLogKpisDTO.builder()
                .storageVolume(DashboardStorageVolumeDTO.builder()
                        .value(842L)
                        .unit("MB")
                        .displayValue("842 MB")
                        .build())
                .build();

        JsonNode json = objectMapper.valueToTree(logKpis);

        assertThat(json.has("storageVolume")).isTrue();
        assertThat(json.path("storageVolume").path("value").asLong()).isEqualTo(842L);
        assertThat(json.path("storageVolume").path("unit").asText()).isEqualTo("MB");
        assertThat(json.path("storageVolume").path("displayValue").asText()).isEqualTo("842 MB");
    }
}
