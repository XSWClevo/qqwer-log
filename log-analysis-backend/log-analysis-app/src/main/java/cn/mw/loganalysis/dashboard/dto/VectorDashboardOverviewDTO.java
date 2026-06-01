package cn.mw.loganalysis.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Vector Host 监控大屏聚合响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VectorDashboardOverviewDTO {

    private String generatedAt;
    private String range;
    private String selectedHostId;
    private VectorHostCardDTO selectedHost;
    private VectorDashboardMetricsDTO metrics;
    private VectorBufferSummaryDTO buffer;
    private List<VectorHostCardDTO> hosts;
    private List<VectorSeriesDTO> eventsOverTime;
    private List<VectorSeriesDTO> dataInSeries;
    private List<VectorSeriesDTO> dataOutSeries;
    private List<VectorSeriesDTO> droppedSeries;
    private List<VectorEventTypeDTO> eventsByType;
    private List<VectorTopSourceDTO> topSources;
    private List<VectorHostSummaryDTO> hostSummary;
    private List<String> warnings;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VectorHostCardDTO {
        private String id;
        private String name;
        private String hostname;
        private String ipAddress;
        private String environment;
        private String status;
        private String statusLabel;
        private Double cpuPercent;
        private Double memoryPercent;
        private Double networkInMbps;
        private Double networkOutMbps;
        private Double eventsPerSecond;
        private Long dataInBytes;
        private Long dataOutBytes;
        private Long droppedEvents;
        private Long bufferUsedBytes;
        private Long bufferTotalBytes;
        private Double bufferUsedPercent;
        private String uptime;
        private String vectorVersion;
        private String osType;
        private List<VectorPointDTO> cpuSeries;
        private List<VectorPointDTO> memorySeries;
        private List<VectorPointDTO> networkInSeries;
        private List<VectorPointDTO> networkOutSeries;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VectorDashboardMetricsDTO {
        private Double eventsPerSecond;
        private Long dataInBytes;
        private Long dataOutBytes;
        private Long droppedEvents;
        private Long bufferUsedBytes;
        private Long bufferTotalBytes;
        private Double eventsChangePercent;
        private Double dataInChangePercent;
        private Double dataOutChangePercent;
        private Double droppedChangePercent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VectorBufferSummaryDTO {
        private Long usedBytes;
        private Long availableBytes;
        private Long totalBytes;
        private Double usedPercent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VectorPointDTO {
        private String label;
        private String timestamp;
        private Double value;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VectorSeriesDTO {
        private String key;
        private String name;
        private String color;
        private List<VectorPointDTO> points;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VectorTopSourceDTO {
        private String name;
        private Double eventsPerSecond;
        private Long events;
        private Double percentage;
        private String color;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VectorEventTypeDTO {
        private String type;
        private Long events;
        private Double percentage;
        private String color;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VectorHostSummaryDTO {
        private String host;
        private String status;
        private Double eventsPerSecond;
        private Long dataInBytes;
        private Double cpuPercent;
        private Double memoryPercent;
    }
}
