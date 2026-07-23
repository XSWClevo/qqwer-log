package cn.mw.loganalysis.jobcommunication.service;

import cn.mw.loganalysis.jobcommunication.dto.JobCommunicationUpsertRequest;
import cn.mw.loganalysis.jobcommunication.entity.JobCommunicationRecord;
import cn.mw.loganalysis.jobcommunication.entity.JobCommunicationStatus;
import cn.mw.loganalysis.jobcommunication.mapper.JobCommunicationRecordMapper;
import cn.mw.loganalysis.jobcommunication.service.impl.JobCommunicationServiceImpl;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobCommunicationServiceImplTest {

    @Test
    void shouldInsertContactedRecordWhenJobIsFirstCommunicated() {
        JobCommunicationRecordMapper mapper = mock(JobCommunicationRecordMapper.class);
        JobCommunicationService service = new JobCommunicationServiceImpl(mapper);

        JobCommunicationUpsertRequest request = new JobCommunicationUpsertRequest();
        request.setPlatform("BOSS");
        request.setJobId("job-1");
        request.setCommunicationKey("boss:job-1:示例公司:张三");
        request.setJobTitle("Java开发");
        request.setCompanyName("示例公司");
        request.setCompanyIndustry("企业服务");
        request.setCompanySize("100-499人");
        request.setCompanyLogo("https://example.com/logo.png");
        request.setHrTitle("招聘经理");
        request.setSalaryRangeNormalized("20-30K*14薪");
        request.setConversationTimeline("[{\"type\":\"sent\"}]");
        request.setLastMessageContent("您好，我对岗位很感兴趣。");
        request.setLastMessageRole("CANDIDATE");

        when(mapper.selectOne(any())).thenReturn(null);

        JobCommunicationRecord result = service.upsert(1001L, request);

        assertThat(result.getStatus()).isEqualTo(JobCommunicationStatus.CONTACTED);
        assertThat(result.getCommunicationCount()).isEqualTo(1);
        assertThat(result.getFirstCommunicatedAt()).isNotNull();
        assertThat(result.getCommunicationKey()).isEqualTo("boss:job-1:示例公司:张三");
        assertThat(result.getCompanyIndustry()).isEqualTo("企业服务");
        assertThat(result.getCompanySize()).isEqualTo("100-499人");
        assertThat(result.getCompanyLogo()).isEqualTo("https://example.com/logo.png");
        assertThat(result.getHrTitle()).isEqualTo("招聘经理");
        assertThat(result.getSalaryRangeNormalized()).isEqualTo("20-30K*14薪");
        assertThat(result.getConversationTimeline()).isEqualTo("[{\"type\":\"sent\"}]");
        assertThat(result.getLastMessageContent()).isEqualTo("您好，我对岗位很感兴趣。");
        assertThat(result.getLastMessageRole()).isEqualTo("CANDIDATE");
        verify(mapper).insert(any(JobCommunicationRecord.class));
        verify(mapper, never()).updateById(any(JobCommunicationRecord.class));
    }

    @Test
    void shouldPromoteExistingRecordToRepliedWithoutResettingFirstCommunicationTime() {
        JobCommunicationRecordMapper mapper = mock(JobCommunicationRecordMapper.class);
        JobCommunicationService service = new JobCommunicationServiceImpl(mapper);

        LocalDateTime firstCommunicatedAt = LocalDateTime.now().minusDays(1);
        JobCommunicationRecord existing = new JobCommunicationRecord();
        existing.setId(1L);
        existing.setUserId(1001L);
        existing.setPlatform("BOSS");
        existing.setJobId("job-1");
        existing.setCommunicationKey("boss:job-1:示例公司:张三");
        existing.setStatus(JobCommunicationStatus.CONTACTED);
        existing.setCommunicationCount(2);
        existing.setFirstCommunicatedAt(firstCommunicatedAt);

        when(mapper.selectOne(any())).thenReturn(existing);

        JobCommunicationRecord result = service.markReplied(1001L, "BOSS", "job-1");

        assertThat(result.getStatus()).isEqualTo(JobCommunicationStatus.REPLIED);
        assertThat(result.getLastRepliedAt()).isNotNull();
        assertThat(result.getFirstCommunicatedAt()).isEqualTo(firstCommunicatedAt);
        verify(mapper).updateById(existing);
    }

    @Test
    void shouldKeepRepliedStatusWhenAlreadyRepliedJobIsUpsertedAgain() {
        JobCommunicationRecordMapper mapper = mock(JobCommunicationRecordMapper.class);
        JobCommunicationService service = new JobCommunicationServiceImpl(mapper);

        LocalDateTime firstCommunicatedAt = LocalDateTime.now().minusDays(2);
        LocalDateTime lastRepliedAt = LocalDateTime.now().minusHours(4);
        JobCommunicationRecord existing = new JobCommunicationRecord();
        existing.setId(1L);
        existing.setUserId(1001L);
        existing.setPlatform("BOSS");
        existing.setJobId("job-1");
        existing.setCommunicationKey("boss:job-1:示例公司:张三");
        existing.setStatus(JobCommunicationStatus.REPLIED);
        existing.setCommunicationCount(3);
        existing.setFirstCommunicatedAt(firstCommunicatedAt);
        existing.setLastRepliedAt(lastRepliedAt);

        JobCommunicationUpsertRequest request = new JobCommunicationUpsertRequest();
        request.setPlatform("BOSS");
        request.setJobId("job-1");
        request.setCommunicationKey("boss:job-1:示例公司:张三");
        request.setJobTitle("Java开发");
        request.setCompanyName("示例公司");

        when(mapper.selectOne(any())).thenReturn(existing);

        JobCommunicationRecord result = service.upsert(1001L, request);

        assertThat(result.getStatus()).isEqualTo(JobCommunicationStatus.REPLIED);
        assertThat(result.getCommunicationCount()).isEqualTo(4);
        assertThat(result.getLastRepliedAt()).isEqualTo(lastRepliedAt);
        verify(mapper).updateById(existing);
    }

    @Test
    void shouldKeepNotSuitableStatusWhenAlreadyRejectedJobIsUpsertedAgain() {
        JobCommunicationRecordMapper mapper = mock(JobCommunicationRecordMapper.class);
        JobCommunicationService service = new JobCommunicationServiceImpl(mapper);

        LocalDateTime firstCommunicatedAt = LocalDateTime.now().minusDays(2);
        LocalDateTime lastRepliedAt = LocalDateTime.now().minusHours(4);
        JobCommunicationRecord existing = new JobCommunicationRecord();
        existing.setId(1L);
        existing.setUserId(1001L);
        existing.setPlatform("BOSS");
        existing.setJobId("job-1");
        existing.setCommunicationKey("boss:job-1:示例公司:张三");
        existing.setStatus(JobCommunicationStatus.NOT_SUITABLE);
        existing.setCommunicationCount(3);
        existing.setFirstCommunicatedAt(firstCommunicatedAt);
        existing.setLastRepliedAt(lastRepliedAt);

        JobCommunicationUpsertRequest request = new JobCommunicationUpsertRequest();
        request.setPlatform("BOSS");
        request.setJobId("job-1");
        request.setCommunicationKey("boss:job-1:示例公司:张三");
        request.setJobTitle("Java开发");
        request.setCompanyName("示例公司");

        when(mapper.selectOne(any())).thenReturn(existing);

        JobCommunicationRecord result = service.upsert(1001L, request);

        assertThat(result.getStatus()).isEqualTo(JobCommunicationStatus.NOT_SUITABLE);
        assertThat(result.getCommunicationCount()).isEqualTo(4);
        assertThat(result.getLastRepliedAt()).isEqualTo(lastRepliedAt);
        verify(mapper).updateById(existing);
    }

    @Test
    void shouldMergeRicherFieldsWhenExistingRecordIsUpsertedAgain() {
        JobCommunicationRecordMapper mapper = mock(JobCommunicationRecordMapper.class);
        JobCommunicationService service = new JobCommunicationServiceImpl(mapper);

        JobCommunicationRecord existing = new JobCommunicationRecord();
        existing.setId(1L);
        existing.setUserId(1001L);
        existing.setPlatform("BOSS");
        existing.setJobId("job-1");
        existing.setCommunicationKey("boss:job-1:旧公司:张三");
        existing.setStatus(JobCommunicationStatus.CONTACTED);
        existing.setCommunicationCount(1);
        existing.setCompanyName("旧公司");
        existing.setHrTitle("旧HR职位");
        existing.setLastMessageContent("旧内容");

        JobCommunicationUpsertRequest request = new JobCommunicationUpsertRequest();
        request.setPlatform("BOSS");
        request.setJobId("job-1");
        request.setCommunicationKey("boss:job-1:新公司:张三");
        request.setCompanyName("新公司");
        request.setCompanyIndustry("互联网");
        request.setCompanySize("1000人以上");
        request.setHrName("张三");
        request.setHrTitle("高级招聘经理");
        request.setSalaryRange("20K-30K");
        request.setSalaryRangeNormalized("20000-30000");
        request.setConversationTimeline("[{\"type\":\"reply\"}]");
        request.setLastMessageContent("您好，看过简历了。");
        request.setLastMessageRole("HR");
        request.setLastMessageAt("2026-06-17 10:20:30");

        when(mapper.selectOne(any())).thenReturn(existing);

        JobCommunicationRecord result = service.upsert(1001L, request);

        assertThat(result.getCompanyName()).isEqualTo("新公司");
        assertThat(result.getCommunicationKey()).isEqualTo("boss:job-1:新公司:张三");
        assertThat(result.getCompanyIndustry()).isEqualTo("互联网");
        assertThat(result.getCompanySize()).isEqualTo("1000人以上");
        assertThat(result.getHrName()).isEqualTo("张三");
        assertThat(result.getHrTitle()).isEqualTo("高级招聘经理");
        assertThat(result.getSalaryRangeNormalized()).isEqualTo("20000-30000");
        assertThat(result.getConversationTimeline()).isEqualTo("[{\"type\":\"reply\"}]");
        assertThat(result.getLastMessageContent()).isEqualTo("您好，看过简历了。");
        assertThat(result.getLastMessageRole()).isEqualTo("HR");
        assertThat(result.getLastMessageAt()).isNotNull();
        verify(mapper).updateById(existing);
    }

    @Test
    void shouldFindExistingRecordByCommunicationKeyWhenJobIdIsNotStable() {
        JobCommunicationRecordMapper mapper = mock(JobCommunicationRecordMapper.class);
        JobCommunicationService service = new JobCommunicationServiceImpl(mapper);

        JobCommunicationRecord existing = new JobCommunicationRecord();
        existing.setId(1L);
        existing.setUserId(1001L);
        existing.setPlatform("BOSS");
        existing.setJobId("job-1");
        existing.setCommunicationKey("boss:job-1:示例公司:张三");
        existing.setStatus(JobCommunicationStatus.CONTACTED);
        existing.setCommunicationCount(1);

        JobCommunicationUpsertRequest request = new JobCommunicationUpsertRequest();
        request.setPlatform("BOSS");
        request.setJobId("fallback-key-from-chat");
        request.setCommunicationKey("boss:job-1:示例公司:张三");
        request.setLastMessageContent("您好，方便发简历吗？");
        request.setLastMessageRole("HR");

        when(mapper.selectOne(any())).thenReturn(existing);

        JobCommunicationRecord result = service.upsert(1001L, request);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getJobId()).isEqualTo("job-1");
        assertThat(result.getCommunicationKey()).isEqualTo("boss:job-1:示例公司:张三");
        assertThat(result.getLastMessageContent()).isEqualTo("您好，方便发简历吗？");
        assertThat(result.getCommunicationCount()).isEqualTo(2);
        verify(mapper).updateById(existing);
    }
}
