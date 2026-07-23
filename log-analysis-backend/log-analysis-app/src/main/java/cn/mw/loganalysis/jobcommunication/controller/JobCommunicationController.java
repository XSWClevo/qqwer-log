package cn.mw.loganalysis.jobcommunication.controller;

import cn.mw.loganalysis.common.response.Result;
import cn.mw.loganalysis.jobcommunication.dto.JobCommunicationOverviewDTO;
import cn.mw.loganalysis.jobcommunication.dto.JobCommunicationPageRequest;
import cn.mw.loganalysis.jobcommunication.dto.JobCommunicationSkipCheckRequest;
import cn.mw.loganalysis.jobcommunication.dto.JobCommunicationUpsertRequest;
import cn.mw.loganalysis.jobcommunication.entity.JobCommunicationRecord;
import cn.mw.loganalysis.jobcommunication.service.JobCommunicationService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/job-communications")
@RequiredArgsConstructor
@Slf4j
public class JobCommunicationController {

    private static final Long ANONYMOUS_SCRIPT_USER_ID = 0L;

    private final JobCommunicationService jobCommunicationService;

    @PostMapping("/upsert")
    public Result<JobCommunicationRecord> upsert(@RequestBody JobCommunicationUpsertRequest request) {
        log.info("job-communications upsert received: jobId={}, communicationKey={}, title={}, company={}, hrName={}, salary={}, location={}",
                request.getJobId(),
                request.getCommunicationKey(),
                request.getJobTitle(),
                request.getCompanyName(),
                request.getHrName(),
                request.getSalaryRange(),
                request.getJobLocation());
        return Result.success(jobCommunicationService.upsert(resolveUserId(), request));
    }

    @PostMapping("/{jobId}/reply")
    public Result<JobCommunicationRecord> markReply(@PathVariable String jobId,
                                                    @RequestParam(defaultValue = "BOSS") String platform) {
        log.info("job-communications reply received: jobId={}, platform={}", jobId, platform);
        return Result.success(jobCommunicationService.markReplied(resolveUserId(), platform, jobId));
    }

    @PostMapping("/{jobId}/status")
    public Result<JobCommunicationRecord> markStatus(@PathVariable String jobId,
                                                     @RequestParam(defaultValue = "BOSS") String platform,
                                                     @RequestParam String status) {
        log.info("job-communications status received: jobId={}, platform={}, status={}", jobId, platform, status);
        return Result.success(jobCommunicationService.markStatus(resolveUserId(), platform, jobId, status));
    }

    @PostMapping("/skip-check")
    public Result<List<Map<String, Object>>> skipCheck(@RequestBody(required = false) JobCommunicationSkipCheckRequest request) {
        JobCommunicationSkipCheckRequest actual = ObjectUtils.defaultIfNull(request, new JobCommunicationSkipCheckRequest());
        log.info("job-communications skip-check received: platform={}, jobIds={}",
                actual.getPlatform(),
                actual.getJobIds() == null ? 0 : actual.getJobIds().size());
        return Result.success(jobCommunicationService.skipCheck(resolveUserId(), actual));
    }

    @GetMapping("/overview")
    public Result<JobCommunicationOverviewDTO> overview() {
        log.info("job-communications overview requested");
        return Result.success(jobCommunicationService.getOverview(resolveUserId()));
    }

    @GetMapping("/trend")
    public Result<List<Map<String, Object>>> trend(@RequestParam(defaultValue = "day") String granularity) {
        log.info("job-communications trend requested: granularity={}", granularity);
        return Result.success(jobCommunicationService.getTrend(resolveUserId(), granularity));
    }

    @GetMapping("/market-analysis")
    public Result<Map<String, Object>> marketAnalysis() {
        log.info("job-communications market-analysis requested");
        return Result.success(jobCommunicationService.getMarketAnalysis(resolveUserId()));
    }

    @PostMapping("/page")
    public Result<IPage<JobCommunicationRecord>> page(@RequestBody(required = false) JobCommunicationPageRequest request) {
        log.info("job-communications page requested: pageNum={}, pageSize={}, platform={}, status={}, keyword={}, startTime={}, endTime={}",
                request == null ? null : request.getPageNum(),
                request == null ? null : request.getPageSize(),
                request == null ? null : request.getPlatform(),
                request == null ? null : request.getStatus(),
                request == null ? null : request.getKeyword(),
                request == null ? null : request.getStartTime(),
                request == null ? null : request.getEndTime());
        return Result.success(jobCommunicationService.page(
                resolveUserId(),
                ObjectUtils.defaultIfNull(request, new JobCommunicationPageRequest())
        ));
    }

    private Long resolveUserId() {
        return ANONYMOUS_SCRIPT_USER_ID;
    }
}
