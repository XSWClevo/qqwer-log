package cn.mw.loganalysis.jobcommunication.service;

import cn.mw.loganalysis.jobcommunication.dto.JobCommunicationOverviewDTO;
import cn.mw.loganalysis.jobcommunication.dto.JobCommunicationPageRequest;
import cn.mw.loganalysis.jobcommunication.dto.JobCommunicationSkipCheckRequest;
import cn.mw.loganalysis.jobcommunication.dto.JobCommunicationUpsertRequest;
import cn.mw.loganalysis.jobcommunication.entity.JobCommunicationRecord;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;
import java.util.Map;

public interface JobCommunicationService {
    JobCommunicationRecord upsert(Long userId, JobCommunicationUpsertRequest request);
    JobCommunicationRecord markReplied(Long userId, String platform, String jobId);
    List<Map<String, Object>> skipCheck(Long userId, JobCommunicationSkipCheckRequest request);
    JobCommunicationOverviewDTO getOverview(Long userId);
    List<Map<String, Object>> getTrend(Long userId, String granularity);
    Map<String, Object> getMarketAnalysis(Long userId);
    IPage<JobCommunicationRecord> page(Long userId, JobCommunicationPageRequest request);
}
