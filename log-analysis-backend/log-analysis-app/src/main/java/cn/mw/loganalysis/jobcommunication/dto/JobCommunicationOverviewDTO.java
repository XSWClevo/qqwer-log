package cn.mw.loganalysis.jobcommunication.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class JobCommunicationOverviewDTO {
    private long todayCommunicated;
    private long todayReplied;
    private long weekCommunicated;
    private long weekReplied;
    private long biweekCommunicated;
    private long biweekReplied;
}
