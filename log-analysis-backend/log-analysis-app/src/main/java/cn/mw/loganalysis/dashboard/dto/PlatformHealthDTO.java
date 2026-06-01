package cn.mw.loganalysis.dashboard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 平台健康摘要。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformHealthDTO {

    private Integer onlineVectorHosts;

    private Integer totalVectorHosts;

    private Long componentErrorsLast5m;

    private Double pipelineThroughputLast5m;

    private Integer queryableDatasetCount;

    private String clickHouseStatus;

    private String lastHeartbeatTime;
}
