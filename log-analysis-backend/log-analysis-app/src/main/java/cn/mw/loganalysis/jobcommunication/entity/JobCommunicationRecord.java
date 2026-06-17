package cn.mw.loganalysis.jobcommunication.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("job_communication_records")
public class JobCommunicationRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;
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
    private JobCommunicationStatus status;
    private LocalDateTime firstCommunicatedAt;
    private LocalDateTime lastRepliedAt;
    private LocalDateTime lastStatusChangedAt;
    private Integer communicationCount;
    private String lastMessageContent;
    private String lastMessageRole;
    private LocalDateTime lastMessageAt;
    private String conversationTimeline;
    private String sourcePayload;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
