package cn.mw.loganalysis.jobcommunication.dto;

import lombok.Data;

import java.util.List;

@Data
public class JobCommunicationSkipCheckRequest {
    private String platform;
    private List<String> jobIds;
}
