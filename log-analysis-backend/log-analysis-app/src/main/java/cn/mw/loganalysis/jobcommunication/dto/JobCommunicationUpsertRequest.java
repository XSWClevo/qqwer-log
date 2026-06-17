package cn.mw.loganalysis.jobcommunication.dto;

import lombok.Data;

@Data
public class JobCommunicationUpsertRequest {
    private String platform;
    private String jobId;
    private String communicationKey;
    private String jobTitle;
    private String companyName;
    private String companyLogo;
    private String companyIndustry;
    private String companySize;
    private String jobLocation;
    private String salaryRange;
    private String salaryRangeNormalized;
    private String jobUrl;
    private String hrName;
    private String hrKey;
    private String hrTitle;
    private String lastMessageContent;
    private String lastMessageRole;
    private String lastMessageAt;
    private String conversationTimeline;
    private String sourcePayload;
}
