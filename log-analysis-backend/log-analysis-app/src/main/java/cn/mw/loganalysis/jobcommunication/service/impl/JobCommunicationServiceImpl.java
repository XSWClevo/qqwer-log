package cn.mw.loganalysis.jobcommunication.service.impl;

import cn.mw.loganalysis.common.exception.UnauthorizedException;
import cn.mw.loganalysis.jobcommunication.dto.JobCommunicationOverviewDTO;
import cn.mw.loganalysis.jobcommunication.dto.JobCommunicationPageRequest;
import cn.mw.loganalysis.jobcommunication.dto.JobCommunicationSkipCheckRequest;
import cn.mw.loganalysis.jobcommunication.dto.JobCommunicationUpsertRequest;
import cn.mw.loganalysis.jobcommunication.entity.JobCommunicationRecord;
import cn.mw.loganalysis.jobcommunication.entity.JobCommunicationStatus;
import cn.mw.loganalysis.jobcommunication.mapper.JobCommunicationRecordMapper;
import cn.mw.loganalysis.jobcommunication.service.JobCommunicationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobCommunicationServiceImpl implements JobCommunicationService {

    private static final String DEFAULT_PLATFORM = "BOSS";
    private static final DateTimeFormatter DEFAULT_DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final JobCommunicationRecordMapper mapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobCommunicationRecord upsert(Long userId, JobCommunicationUpsertRequest request) {
        validateUser(userId);
        String platform = normalizePlatform(request.getPlatform());
        String jobId = normalizeRequired(request.getJobId(), "jobId");
        String communicationKey = normalizeCommunicationKey(request, platform, jobId);
        LocalDateTime now = LocalDateTime.now();

        JobCommunicationRecord existing = findOne(userId, platform, jobId, communicationKey);
        if (existing == null) {
            JobCommunicationRecord record = buildRecord(userId, request, platform, jobId, communicationKey);
            record.setStatus(JobCommunicationStatus.CONTACTED);
            record.setFirstCommunicatedAt(now);
            record.setLastStatusChangedAt(now);
            record.setCommunicationCount(1);
            record.setCreatedAt(now);
            record.setUpdatedAt(now);
            mapper.insert(record);
            log.info("job-communications upsert inserted: userId={}, id={}, jobId={}, communicationKey={}, title={}, company={}, hrName={}, salary={}, status={}, firstCommunicatedAt={}",
                    userId,
                    record.getId(),
                    record.getJobId(),
                    record.getCommunicationKey(),
                    record.getJobTitle(),
                    record.getCompanyName(),
                    record.getHrName(),
                    record.getSalaryRange(),
                    record.getStatus(),
                    record.getFirstCommunicatedAt());
            return record;
        }

        existing.setCommunicationKey(nvl(communicationKey, existing.getCommunicationKey()));
        existing.setJobId(mergeJobId(request.getJobId(), existing.getJobId(), communicationKey, existing));
        existing.setJobTitle(nvl(request.getJobTitle(), existing.getJobTitle()));
        existing.setCompanyName(nvl(request.getCompanyName(), existing.getCompanyName()));
        existing.setCompanyLogo(nvl(request.getCompanyLogo(), existing.getCompanyLogo()));
        existing.setCompanyIndustry(nvl(request.getCompanyIndustry(), existing.getCompanyIndustry()));
        existing.setCompanySize(nvl(request.getCompanySize(), existing.getCompanySize()));
        existing.setJobLocation(nvl(request.getJobLocation(), existing.getJobLocation()));
        existing.setSalaryRange(nvl(request.getSalaryRange(), existing.getSalaryRange()));
        existing.setSalaryRangeNormalized(nvl(request.getSalaryRangeNormalized(), existing.getSalaryRangeNormalized()));
        existing.setJobUrl(nvl(request.getJobUrl(), existing.getJobUrl()));
        existing.setHrName(nvl(request.getHrName(), existing.getHrName()));
        existing.setHrKey(nvl(request.getHrKey(), existing.getHrKey()));
        existing.setHrTitle(nvl(request.getHrTitle(), existing.getHrTitle()));
        existing.setLastMessageContent(nvl(request.getLastMessageContent(), existing.getLastMessageContent()));
        existing.setLastMessageRole(nvl(request.getLastMessageRole(), existing.getLastMessageRole()));
        existing.setConversationTimeline(nvl(request.getConversationTimeline(), existing.getConversationTimeline()));
        existing.setLastMessageAt(nvlDateTime(parseDateTime(request.getLastMessageAt()), existing.getLastMessageAt()));
        existing.setSourcePayload(nvl(request.getSourcePayload(), existing.getSourcePayload()));
        existing.setCommunicationCount(ObjectUtils.defaultIfNull(existing.getCommunicationCount(), 0) + 1);
        if (existing.getStatus() == null) {
            existing.setStatus(JobCommunicationStatus.CONTACTED);
        }
        if (existing.getFirstCommunicatedAt() == null) {
            existing.setFirstCommunicatedAt(now);
        }
        if (existing.getStatus() != JobCommunicationStatus.REPLIED) {
            existing.setStatus(JobCommunicationStatus.CONTACTED);
            existing.setLastStatusChangedAt(now);
        }
        existing.setUpdatedAt(now);
        mapper.updateById(existing);
        log.info("job-communications upsert updated: userId={}, id={}, jobId={}, communicationKey={}, title={}, company={}, hrName={}, salary={}, status={}, count={}, updatedAt={}",
                userId,
                existing.getId(),
                existing.getJobId(),
                existing.getCommunicationKey(),
                existing.getJobTitle(),
                existing.getCompanyName(),
                existing.getHrName(),
                existing.getSalaryRange(),
                existing.getStatus(),
                existing.getCommunicationCount(),
                existing.getUpdatedAt());
        return existing;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public JobCommunicationRecord markReplied(Long userId, String platform, String jobId) {
        validateUser(userId);
        platform = normalizePlatform(platform);
        jobId = normalizeRequired(jobId, "jobId");
        JobCommunicationRecord record = findOne(userId, platform, jobId, null);
        if (record == null) {
            log.info("job-communications reply ignored, record not found: userId={}, platform={}, jobId={}", userId, platform, jobId);
            return null;
        }
        LocalDateTime now = LocalDateTime.now();
        record.setStatus(JobCommunicationStatus.REPLIED);
        record.setLastRepliedAt(now);
        record.setLastStatusChangedAt(now);
        record.setUpdatedAt(now);
        mapper.updateById(record);
        log.info("job-communications reply updated: userId={}, id={}, jobId={}, status={}, lastRepliedAt={}",
                userId,
                record.getId(),
                record.getJobId(),
                record.getStatus(),
                record.getLastRepliedAt());
        return record;
    }

    @Override
    public List<Map<String, Object>> skipCheck(Long userId, JobCommunicationSkipCheckRequest request) {
        validateUser(userId);
        if (request == null || CollectionUtils.isEmpty(request.getJobIds())) {
            return List.of();
        }
        String platform = normalizePlatform(request.getPlatform());
        List<Map<String, Object>> result = new ArrayList<>();
        for (String jobId : request.getJobIds()) {
            if (StringUtils.isBlank(jobId)) {
                continue;
            }
            JobCommunicationRecord record = findOne(userId, platform, jobId, null);
            if (record != null && record.getStatus() == JobCommunicationStatus.REPLIED) {
                result.add(Map.of("jobId", jobId, "status", record.getStatus().name()));
            }
        }
        return result;
    }

    @Override
    public JobCommunicationOverviewDTO getOverview(Long userId) {
        validateUser(userId);
        LocalDateTime now = LocalDateTime.now();
        return JobCommunicationOverviewDTO.builder()
                .todayCommunicated(countByRange(userId, now.toLocalDate().atStartOfDay(), now.plusDays(1).toLocalDate().atStartOfDay(), null))
                .todayReplied(countByRange(userId, now.toLocalDate().atStartOfDay(), now.plusDays(1).toLocalDate().atStartOfDay(), JobCommunicationStatus.REPLIED))
                .weekCommunicated(countByRange(userId, now.minusDays(7), now.plusDays(1), null))
                .weekReplied(countByRange(userId, now.minusDays(7), now.plusDays(1), JobCommunicationStatus.REPLIED))
                .biweekCommunicated(countByRange(userId, now.minusDays(14), now.plusDays(1), null))
                .biweekReplied(countByRange(userId, now.minusDays(14), now.plusDays(1), JobCommunicationStatus.REPLIED))
                .build();
    }

    @Override
    public List<Map<String, Object>> getTrend(Long userId, String granularity) {
        validateUser(userId);
        LocalDate start = LocalDate.now().minusDays(13);
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < 14; i++) {
            LocalDate day = start.plusDays(i);
            Map<String, Object> item = new HashMap<>();
            item.put("date", day.toString());
            long communicated = countByRange(userId, day.atStartOfDay(), day.plusDays(1).atStartOfDay(), null);
            long replied = countByRange(userId, day.atStartOfDay(), day.plusDays(1).atStartOfDay(), JobCommunicationStatus.REPLIED);
            item.put("communicated", communicated);
            item.put("replied", replied);
            item.put("replyRate", communicated <= 0 ? 0D : (double) replied / communicated);
            items.add(item);
        }
        return items;
    }

    @Override
    public Map<String, Object> getMarketAnalysis(Long userId) {
        validateUser(userId);
        List<JobCommunicationRecord> records = mapper.selectList(new LambdaQueryWrapper<JobCommunicationRecord>()
                .eq(JobCommunicationRecord::getUserId, userId));
        Map<String, Object> result = new HashMap<>();
        result.put("companyTop", topBuckets(records, JobCommunicationRecord::getCompanyName));
        result.put("jobTop", topBuckets(records, JobCommunicationRecord::getJobTitle));
        result.put("locationTop", topBuckets(records, JobCommunicationRecord::getJobLocation));
        result.put("salaryTop", topBuckets(records, JobCommunicationRecord::getSalaryRange));
        return result;
    }

    @Override
    public IPage<JobCommunicationRecord> page(Long userId, JobCommunicationPageRequest request) {
        validateUser(userId);
        Page<JobCommunicationRecord> page = new Page<>(
                ObjectUtils.defaultIfNull(request.getPageNum(), 1),
                ObjectUtils.defaultIfNull(request.getPageSize(), 20)
        );
        LambdaQueryWrapper<JobCommunicationRecord> wrapper = new LambdaQueryWrapper<JobCommunicationRecord>()
                .eq(JobCommunicationRecord::getUserId, userId)
                .orderByDesc(JobCommunicationRecord::getUpdatedAt);
        if (StringUtils.isNotBlank(request.getPlatform())) {
            wrapper.eq(JobCommunicationRecord::getPlatform, request.getPlatform());
        }
        if (StringUtils.isNotBlank(request.getStatus())) {
            wrapper.eq(JobCommunicationRecord::getStatus, JobCommunicationStatus.valueOf(request.getStatus()));
        }
        if (StringUtils.isNotBlank(request.getKeyword())) {
            wrapper.and(q -> q.like(JobCommunicationRecord::getJobTitle, request.getKeyword())
                    .or().like(JobCommunicationRecord::getCompanyName, request.getKeyword()));
        }
        LocalDateTime startTime = parseDateTime(request.getStartTime());
        LocalDateTime endTime = parseDateTime(request.getEndTime());
        if (startTime != null) {
            wrapper.ge(JobCommunicationRecord::getFirstCommunicatedAt, startTime);
        }
        if (endTime != null) {
            wrapper.le(JobCommunicationRecord::getFirstCommunicatedAt, endTime);
        }
        IPage<JobCommunicationRecord> result = mapper.selectPage(page, wrapper);
        log.info("job-communications page result: userId={}, total={}, current={}, size={}, latest={}",
                userId,
                result.getTotal(),
                result.getCurrent(),
                result.getSize(),
                result.getRecords().stream().findFirst()
                        .map(record -> String.format("id=%s,jobId=%s,title=%s,company=%s,hr=%s,salary=%s,updatedAt=%s",
                                record.getId(),
                                record.getJobId(),
                                record.getJobTitle(),
                                record.getCompanyName(),
                                record.getHrName(),
                                record.getSalaryRange(),
                                record.getUpdatedAt()))
                        .orElse("none"));
        return result;
    }

    private JobCommunicationRecord buildRecord(Long userId, JobCommunicationUpsertRequest request, String platform, String jobId, String communicationKey) {
        JobCommunicationRecord record = new JobCommunicationRecord();
        record.setUserId(userId);
        record.setPlatform(platform);
        record.setJobId(jobId);
        record.setCommunicationKey(communicationKey);
        record.setJobTitle(request.getJobTitle());
        record.setCompanyName(request.getCompanyName());
        record.setCompanyLogo(request.getCompanyLogo());
        record.setCompanyIndustry(request.getCompanyIndustry());
        record.setCompanySize(request.getCompanySize());
        record.setJobLocation(request.getJobLocation());
        record.setSalaryRange(request.getSalaryRange());
        record.setSalaryRangeNormalized(request.getSalaryRangeNormalized());
        record.setJobUrl(request.getJobUrl());
        record.setHrName(request.getHrName());
        record.setHrKey(request.getHrKey());
        record.setHrTitle(request.getHrTitle());
        record.setLastMessageContent(request.getLastMessageContent());
        record.setLastMessageRole(request.getLastMessageRole());
        record.setLastMessageAt(parseDateTime(request.getLastMessageAt()));
        record.setConversationTimeline(request.getConversationTimeline());
        record.setSourcePayload(request.getSourcePayload());
        return record;
    }

    private JobCommunicationRecord findOne(Long userId, String platform, String jobId, String communicationKey) {
        if (StringUtils.isNotBlank(communicationKey)) {
            JobCommunicationRecord byCommunicationKey = mapper.selectOne(new LambdaQueryWrapper<JobCommunicationRecord>()
                    .eq(JobCommunicationRecord::getUserId, userId)
                    .eq(JobCommunicationRecord::getPlatform, platform)
                    .eq(JobCommunicationRecord::getCommunicationKey, communicationKey)
                    .last("limit 1"));
            if (byCommunicationKey != null) {
                return byCommunicationKey;
            }
        }
        return mapper.selectOne(new LambdaQueryWrapper<JobCommunicationRecord>()
                .eq(JobCommunicationRecord::getUserId, userId)
                .eq(JobCommunicationRecord::getPlatform, platform)
                .eq(JobCommunicationRecord::getJobId, jobId)
                .last("limit 1"));
    }

    private long countByRange(Long userId, LocalDateTime start, LocalDateTime end, JobCommunicationStatus status) {
        LambdaQueryWrapper<JobCommunicationRecord> wrapper = new LambdaQueryWrapper<JobCommunicationRecord>()
                .eq(JobCommunicationRecord::getUserId, userId)
                .ge(JobCommunicationRecord::getFirstCommunicatedAt, start)
                .lt(JobCommunicationRecord::getFirstCommunicatedAt, end);
        if (status != null) {
            wrapper.eq(JobCommunicationRecord::getStatus, status);
        }
        return mapper.selectCount(wrapper);
    }

    private List<Map<String, Object>> topBuckets(List<JobCommunicationRecord> records,
                                                 Function<JobCommunicationRecord, String> classifier) {
        return records.stream()
                .map(classifier)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.groupingBy(String::trim, Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .limit(10)
                .map(entry -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("name", entry.getKey());
                    item.put("count", entry.getValue());
                    return item;
                })
                .collect(Collectors.toList());
    }

    private LocalDateTime parseDateTime(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, DEFAULT_DATE_TIME_FORMATTER);
        } catch (DateTimeException ignored) {
        }
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeException ignored) {
            return null;
        }
    }

    private void validateUser(Long userId) {
        if (ObjectUtils.isEmpty(userId)) {
            throw new UnauthorizedException("未获取到当前登录用户信息");
        }
    }

    private String normalizeRequired(String value, String field) {
        if (StringUtils.isBlank(value)) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.trim();
    }

    private String normalizePlatform(String platform) {
        return StringUtils.defaultIfBlank(platform, DEFAULT_PLATFORM).trim().toUpperCase();
    }

    private String normalizeCommunicationKey(JobCommunicationUpsertRequest request, String platform, String jobId) {
        if (StringUtils.isNotBlank(request.getCommunicationKey())) {
            return request.getCommunicationKey().trim();
        }
        return String.join(":",
                platform,
                StringUtils.defaultString(jobId).trim(),
                normalizeKeyPart(request.getCompanyName()),
                normalizeKeyPart(StringUtils.defaultIfBlank(request.getHrName(), request.getHrKey()))
        );
    }

    private String normalizeKeyPart(String value) {
        return StringUtils.defaultString(value).trim().replaceAll("\\s+", "").toLowerCase();
    }

    private String mergeJobId(String requestedJobId, String existingJobId, String communicationKey, JobCommunicationRecord existing) {
        if (StringUtils.isBlank(requestedJobId)) {
            return existingJobId;
        }
        if (StringUtils.isNotBlank(existingJobId)
                && StringUtils.isNotBlank(communicationKey)
                && StringUtils.equals(existing.getCommunicationKey(), communicationKey)
                && !StringUtils.equals(existingJobId, requestedJobId)) {
            return existingJobId;
        }
        return nvl(requestedJobId, existingJobId);
    }

    private String nvl(String newValue, String oldValue) {
        return StringUtils.isNotBlank(newValue) ? newValue : oldValue;
    }

    private LocalDateTime nvlDateTime(LocalDateTime newValue, LocalDateTime oldValue) {
        return newValue != null ? newValue : oldValue;
    }
}
