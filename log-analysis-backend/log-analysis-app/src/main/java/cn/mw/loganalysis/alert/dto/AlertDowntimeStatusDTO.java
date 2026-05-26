package cn.mw.loganalysis.alert.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 当前 downtime 状态。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertDowntimeStatusDTO {

    private Boolean active;

    private Long downtimeId;

    private String reason;

    private LocalDateTime startsAt;

    private LocalDateTime endsAt;
}
