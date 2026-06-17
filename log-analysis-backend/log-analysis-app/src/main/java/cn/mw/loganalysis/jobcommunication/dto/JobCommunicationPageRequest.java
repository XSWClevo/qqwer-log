package cn.mw.loganalysis.jobcommunication.dto;

import lombok.Data;

@Data
public class JobCommunicationPageRequest {
    private Integer pageNum = 1;
    private Integer pageSize = 20;
    private String platform;
    private String status;
    private String startTime;
    private String endTime;
    private String keyword;
}
