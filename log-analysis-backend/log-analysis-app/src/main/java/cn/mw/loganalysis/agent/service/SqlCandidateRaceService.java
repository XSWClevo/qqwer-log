package cn.mw.loganalysis.agent.service;

import cn.mw.loganalysis.agent.repository.AgentSqlQueryExampleRepository;
import cn.mw.loganalysis.stats.dto.AiQueryResponse;
import cn.mw.loganalysis.stats.service.DynamicLogQueryService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * 并发竞争 Text2SQL 候选，优先采用能通过校验和执行的最快结果。
 */
@Slf4j
@Service
public class SqlCandidateRaceService {

    private static final long LLM_DELAY_MS = 150L;
    private static final long RACE_TIMEOUT_MS = 10_000L;
    private static final String LLM_SOURCE = "llm";
    private static final String HISTORY_SOURCE = "history";

    private final List<SqlCandidateProvider> providers;
    private final SqlCandidateValidator validator;
    private final DynamicLogQueryService dynamicLogQueryService;
    private final AgentSqlQueryExampleRepository exampleRepository;
    private final SqlQuestionNormalizer questionNormalizer;
    private final Executor executor;

    public SqlCandidateRaceService(List<SqlCandidateProvider> providers,
                                   SqlCandidateValidator validator,
                                   DynamicLogQueryService dynamicLogQueryService,
                                   AgentSqlQueryExampleRepository exampleRepository,
                                   SqlQuestionNormalizer questionNormalizer,
                                   @Qualifier("agentText2SqlExecutor") Executor executor) {
        this.providers = CollectionUtils.emptyIfNull(providers).stream()
                .sorted(Comparator.comparingInt(SqlCandidateProvider::getOrder))
                .toList();
        this.validator = validator;
        this.dynamicLogQueryService = dynamicLogQueryService;
        this.exampleRepository = exampleRepository;
        this.questionNormalizer = questionNormalizer;
        this.executor = executor;
    }

    public SqlCandidateResult race(AgentExecutionContext context, String query) {
        long startedAt = System.currentTimeMillis();
        List<SqlCandidateProvider> supportedProviders = providers.stream()
                .filter(provider -> supports(provider, context, query))
                .toList();
        if (CollectionUtils.isEmpty(supportedProviders)) {
            throw new IllegalStateException("没有可用 SQL 候选来源");
        }

        CompletionService<CandidateAttempt> completionService = new ExecutorCompletionService<>(executor);
        List<Future<CandidateAttempt>> futures = new ArrayList<>();
        List<String> validatedCandidates = new ArrayList<>();
        List<String> rejectedCandidates = new ArrayList<>();
        List<SqlCandidateProvider> llmProviders = supportedProviders.stream()
                .filter(this::isLlmProvider)
                .toList();
        List<SqlCandidateProvider> cheapProviders = supportedProviders.stream()
                .filter(provider -> !isLlmProvider(provider))
                .toList();

        int submitted = submitProviders(completionService, futures, rejectedCandidates, cheapProviders, context, query);
        boolean llmSubmitted = false;
        try {
            long llmDeadline = startedAt + LLM_DELAY_MS;
            while (true) {
                Future<CandidateAttempt> completed = pollNext(completionService, submitted, startedAt, llmDeadline, llmSubmitted);
                if (completed == null) {
                    if (!llmSubmitted && !llmProviders.isEmpty()) {
                        if (submitted > 0) {
                            waitUntilLlmDelay(startedAt);
                        }
                        submitted += submitProviders(completionService, futures, rejectedCandidates, llmProviders, context, query);
                        llmSubmitted = true;
                        continue;
                    }
                    if (submitted <= 0) {
                        break;
                    }
                    completed = completionService.poll(remainingMs(startedAt), TimeUnit.MILLISECONDS);
                    if (completed == null) {
                        rejectedCandidates.add("race: 候选竞争超时");
                        break;
                    }
                }

                CandidateAttempt attempt = getAttempt(completed);
                if (attempt == null) {
                    submitted--;
                    continue;
                }
                submitted--;
                if (attempt.validated()) {
                    validatedCandidates.add(attempt.source());
                }
                if (attempt.success()) {
                    SqlCandidateResult result = buildResult(attempt, startedAt, validatedCandidates, rejectedCandidates);
                    cancelPending(futures);
                    recordSuccess(context, query, attempt);
                    return result;
                }
                rejectedCandidates.add(attempt.rejectedReason());
                if (submitted == 0 && (llmSubmitted || llmProviders.isEmpty())) {
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            rejectedCandidates.add("race: 候选竞争被中断");
        } finally {
            cancelPending(futures);
        }

        throw new IllegalStateException("所有 SQL 候选均失败: " + StringUtils.join(rejectedCandidates, "; "));
    }

    private boolean supports(SqlCandidateProvider provider, AgentExecutionContext context, String query) {
        try {
            return provider.supports(context, query);
        } catch (RuntimeException e) {
            log.warn("SQL 候选来源支持性判断失败: source={}", provider.source(), e);
            return false;
        }
    }

    private boolean isLlmProvider(SqlCandidateProvider provider) {
        return StringUtils.equalsIgnoreCase(provider.source(), LLM_SOURCE);
    }

    private int submitProviders(CompletionService<CandidateAttempt> completionService,
                                List<Future<CandidateAttempt>> futures,
                                List<String> rejectedCandidates,
                                List<SqlCandidateProvider> providersToSubmit,
                                AgentExecutionContext context,
                                String query) {
        int submitted = 0;
        for (SqlCandidateProvider provider : providersToSubmit) {
            try {
                futures.add(completionService.submit(candidateTask(provider, context, query)));
                submitted++;
            } catch (RejectedExecutionException e) {
                rejectedCandidates.add(provider.source() + ": 候选任务提交失败 - " + readableMessage(e));
            }
        }
        return submitted;
    }

    private Callable<CandidateAttempt> candidateTask(SqlCandidateProvider provider,
                                                     AgentExecutionContext context,
                                                     String query) {
        return () -> {
            try {
                return provider.generate(context, query)
                        .map(candidate -> evaluateCandidate(context, provider, candidate))
                        .orElseGet(() -> CandidateAttempt.rejected(provider.source(), provider.source() + ": 未生成候选 SQL"));
            } catch (RuntimeException e) {
                return CandidateAttempt.rejected(provider.source(), provider.source() + ": " + readableMessage(e));
            }
        };
    }

    private CandidateAttempt evaluateCandidate(AgentExecutionContext context,
                                               SqlCandidateProvider provider,
                                               SqlCandidate candidate) {
        SqlCandidateValidationResult validation = validator.validate(context, candidate);
        if (!validation.valid()) {
            return CandidateAttempt.rejected(provider.source(), provider.source() + ": " + validation.reason());
        }
        try {
            long executionStartedAt = System.currentTimeMillis();
            Object rawResult = dynamicLogQueryService.executeRawSQL(context.datasourceId(), validation.sql());
            long executionMs = System.currentTimeMillis() - executionStartedAt;
            AiQueryResponse response = AiQueryResponse.builder()
                    .success(true)
                    .sql(validation.sql())
                    .result(rawResult)
                    .sqlGenerationTime(sqlGenerationSeconds(candidate))
                    .sqlExecutionTime(executionMs / 1000D)
                    .build();
            return CandidateAttempt.success(provider.source(), candidate, response);
        } catch (RuntimeException e) {
            return CandidateAttempt.validatedRejected(provider.source(), provider.source() + ": SQL 执行失败 - " + readableMessage(e));
        }
    }

    private Future<CandidateAttempt> pollNext(CompletionService<CandidateAttempt> completionService,
                                              int submitted,
                                              long startedAt,
                                              long llmDeadline,
                                              boolean llmSubmitted) throws InterruptedException {
        if (submitted <= 0) {
            return null;
        }
        long waitMs = llmSubmitted ? remainingMs(startedAt) : Math.min(remainingMs(startedAt), Math.max(0L, llmDeadline - System.currentTimeMillis()));
        if (waitMs <= 0L) {
            return completionService.poll();
        }
        return completionService.poll(waitMs, TimeUnit.MILLISECONDS);
    }

    private CandidateAttempt getAttempt(Future<CandidateAttempt> completed) {
        try {
            return completed.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CandidateAttempt.rejected("race", "race: 候选竞争被中断");
        } catch (ExecutionException e) {
            return CandidateAttempt.rejected("race", "race: " + readableMessage(e));
        }
    }

    private void waitUntilLlmDelay(long startedAt) throws InterruptedException {
        long waitMs = Math.max(0L, LLM_DELAY_MS - (System.currentTimeMillis() - startedAt));
        if (waitMs > 0L) {
            TimeUnit.MILLISECONDS.sleep(waitMs);
        }
    }

    private long remainingMs(long startedAt) {
        return Math.max(0L, RACE_TIMEOUT_MS - (System.currentTimeMillis() - startedAt));
    }

    private SqlCandidateResult buildResult(CandidateAttempt attempt,
                                           long startedAt,
                                           List<String> validatedCandidates,
                                           List<String> rejectedCandidates) {
        AiQueryResponse response = attempt.response();
        response.setTotalExecutionTime((System.currentTimeMillis() - startedAt) / 1000D);
        return SqlCandidateResult.builder()
                .response(response)
                .candidateSource(attempt.source())
                .raceMs(System.currentTimeMillis() - startedAt)
                .validatedCandidates(List.copyOf(validatedCandidates))
                .rejectedCandidates(List.copyOf(rejectedCandidates))
                .build();
    }

    private void recordSuccess(AgentExecutionContext context, String query, CandidateAttempt attempt) {
        try {
            SqlCandidate candidate = attempt.candidate();
            if (StringUtils.equalsIgnoreCase(attempt.source(), HISTORY_SOURCE)) {
                Long exampleId = historyExampleId(candidate.metadata());
                exampleRepository.markUsed(exampleId);
                return;
            }
            exampleRepository.saveSuccess(
                    context.userId(),
                    context.datasourceId(),
                    context.datasourceType(),
                    query,
                    questionNormalizer.normalize(query),
                    attempt.response().getSql(),
                    candidate.resultType()
            );
        } catch (RuntimeException e) {
            log.warn("记录 Text2SQL 成功经验失败: source={}, datasourceId={}", attempt.source(), context.datasourceId(), e);
        }
    }

    private Long historyExampleId(Map<String, Object> metadata) {
        Object value = metadata.get("exampleId");
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (ObjectUtils.isNotEmpty(value)) {
            try {
                return Long.valueOf(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private double sqlGenerationSeconds(SqlCandidate candidate) {
        Map<String, Object> metadata = candidate.metadata();
        if (metadata != null && metadata.get("sqlGenerationTime") instanceof Number value) {
            return value.doubleValue();
        }
        return candidate.generationTimeMs() / 1000D;
    }

    private void cancelPending(List<Future<CandidateAttempt>> futures) {
        for (Future<CandidateAttempt> future : futures) {
            if (!future.isDone()) {
                future.cancel(true);
            }
        }
    }

    private String readableMessage(Throwable throwable) {
        Throwable cause = throwable instanceof ExecutionException && throwable.getCause() != null
                ? throwable.getCause()
                : throwable;
        return StringUtils.defaultIfBlank(cause.getMessage(), cause.getClass().getSimpleName());
    }

    private record CandidateAttempt(String source,
                                    SqlCandidate candidate,
                                    AiQueryResponse response,
                                    String rejectedReason,
                                    boolean validated) {

        static CandidateAttempt success(String source, SqlCandidate candidate, AiQueryResponse response) {
            return new CandidateAttempt(source, candidate, response, null, true);
        }

        static CandidateAttempt rejected(String source, String reason) {
            return new CandidateAttempt(source, null, null, StringUtils.defaultIfBlank(reason, source + ": 候选失败"), false);
        }

        static CandidateAttempt validatedRejected(String source, String reason) {
            return new CandidateAttempt(source, null, null, StringUtils.defaultIfBlank(reason, source + ": 候选失败"), true);
        }

        boolean success() {
            return response != null;
        }
    }
}
