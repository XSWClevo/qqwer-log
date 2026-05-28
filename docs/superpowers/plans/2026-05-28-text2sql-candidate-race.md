# Text2SQL 候选竞争实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将智能助手 `TEXT2SQL` 链路改为“模板解析 + 历史相似查询 + 大模型生成”的候选竞争机制，首个安全且执行成功的候选胜出。

**Architecture:** 使用 `SqlCandidateProvider` 策略接口生成候选，`SqlCandidateRaceService` 统一调度、校验、执行、落历史，`Text2SqlToolHandler` 只负责接入 Agent 卡片结果。保留 `AiQueryService.query()` 给非助手调用方，助手只通过 `generateSqlOnly()` 获取 LLM SQL 候选。

**Tech Stack:** Java 21、Spring Boot 3.5、MyBatis Plus、Liquibase、JUnit 5、Mockito、AssertJ、ClickHouse 动态查询服务。

---

## 文件结构

**后端生产代码**

- 修改：`log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/stats/service/AiQueryService.java`
  - 暴露 `generateSqlOnly(AiQueryRequest)`，只生成 SQL，不执行 SQL。

- 创建或保留：`log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/SqlCandidate.java`
  - 候选 SQL 值对象。

- 创建或保留：`log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/SqlCandidateProvider.java`
  - 候选来源策略接口。

- 创建或保留：`log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/SqlCandidateValidationResult.java`
  - SQL 校验结果值对象。

- 创建或保留：`log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/SqlCandidateResult.java`
  - 候选竞争最终结果。

- 创建或保留：`log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/SqlTemplateSupport.java`
  - 模板 SQL 辅助方法。

- 创建或保留：`log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/SqlQuestionNormalizer.java`
  - 历史相似查询的文本归一化和相似度。

- 创建或保留：`log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/SqlCandidateValidator.java`
  - SELECT 安全校验、表校验、字段校验、LIMIT 修正。

- 创建或保留：`log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/TemplateSqlCandidateProvider.java`
  - 高频模板候选。

- 创建或保留：`log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/HistorySqlCandidateProvider.java`
  - 历史成功 SQL 候选。

- 创建或保留：`log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/LlmSqlCandidateProvider.java`
  - LLM SQL 候选。

- 创建：`log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/SqlCandidateRaceService.java`
  - 并发竞争编排、校验执行、历史落库。

- 创建：`log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/config/AgentText2SqlExecutorConfig.java`
  - Text2SQL 候选竞争专用线程池。

- 修改：`log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/Text2SqlToolHandler.java`
  - 从旧 preflight + `AiQueryService.query()` 改为调用 `SqlCandidateRaceService`。

- 删除：`log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/Text2SqlPreflightStrategy.java`
  - 旧 preflight 策略接口。

- 删除：`log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/LogCountMetricPreflightStrategy.java`
  - 已由模板候选替代。

- 删除：`log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/DimensionCountPreflightStrategy.java`
  - 已由模板候选替代。

**后端持久化**

- 创建或保留：`log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/entity/AgentSqlQueryExample.java`
- 创建或保留：`log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/mapper/AgentSqlQueryExampleMapper.java`
- 创建或保留：`log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/repository/AgentSqlQueryExampleRepository.java`
- 创建或保留：`log-analysis-backend/log-analysis-app/src/main/resources/db/changelog/changes/20260528-agent-sql-query-examples.sql`
- 修改：`log-analysis-backend/log-analysis-app/src/main/resources/db/changelog/db.changelog-master.yaml`

**测试代码**

- 删除：`log-analysis-backend/log-analysis-app/src/test/java/cn/mw/loganalysis/agent/service/LogCountMetricPreflightStrategyTest.java`
- 删除：`log-analysis-backend/log-analysis-app/src/test/java/cn/mw/loganalysis/agent/service/DimensionCountPreflightStrategyTest.java`
- 创建：`log-analysis-backend/log-analysis-app/src/test/java/cn/mw/loganalysis/agent/service/TemplateSqlCandidateProviderTest.java`
- 创建：`log-analysis-backend/log-analysis-app/src/test/java/cn/mw/loganalysis/agent/service/HistorySqlCandidateProviderTest.java`
- 创建：`log-analysis-backend/log-analysis-app/src/test/java/cn/mw/loganalysis/agent/service/SqlCandidateValidatorTest.java`
- 创建：`log-analysis-backend/log-analysis-app/src/test/java/cn/mw/loganalysis/agent/service/SqlCandidateRaceServiceTest.java`
- 创建：`log-analysis-backend/log-analysis-app/src/test/java/cn/mw/loganalysis/agent/service/Text2SqlToolHandlerTest.java`

## Task 1: SQL 候选核心模型和模板 Provider

**Files:**

- Create/Modify: `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/SqlCandidate.java`
- Create/Modify: `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/SqlCandidateProvider.java`
- Create/Modify: `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/SqlTemplateSupport.java`
- Create/Modify: `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/TemplateSqlCandidateProvider.java`
- Test: `log-analysis-backend/log-analysis-app/src/test/java/cn/mw/loganalysis/agent/service/TemplateSqlCandidateProviderTest.java`
- Delete: `log-analysis-backend/log-analysis-app/src/test/java/cn/mw/loganalysis/agent/service/LogCountMetricPreflightStrategyTest.java`
- Delete: `log-analysis-backend/log-analysis-app/src/test/java/cn/mw/loganalysis/agent/service/DimensionCountPreflightStrategyTest.java`

- [ ] **Step 1: 写模板候选失败测试**

Create `log-analysis-backend/log-analysis-app/src/test/java/cn/mw/loganalysis/agent/service/TemplateSqlCandidateProviderTest.java`:

```java
package cn.mw.loganalysis.agent.service;

import cn.mw.loganalysis.stats.service.DynamicLogQueryService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TemplateSqlCandidateProviderTest {

    private final DynamicLogQueryService dynamicLogQueryService = mock(DynamicLogQueryService.class);
    private final TemplateSqlCandidateProvider provider = new TemplateSqlCandidateProvider(dynamicLogQueryService);
    private final AgentExecutionContext context = new AgentExecutionContext(
            "sink-1",
            "syslog_logs_sink",
            "clickhouse",
            1L,
            "session-1"
    );

    @Test
    void shouldGenerateSimpleCountCandidate() {
        when(dynamicLogQueryService.getTableName("sink-1")).thenReturn("syslog_logs");

        Optional<SqlCandidate> candidate = provider.generate(context, "查最近一小时的日志数据总数");

        assertThat(candidate).isPresent();
        assertThat(candidate.get().source()).isEqualTo("template");
        assertThat(candidate.get().resultType()).isEqualTo("metric");
        assertThat(candidate.get().sql()).contains("SELECT count() AS total FROM `syslog_logs`");
        assertThat(candidate.get().sql()).contains("`timestamp` >=");
        assertThat(candidate.get().confidence()).isGreaterThanOrEqualTo(0.9D);
    }

    @Test
    void shouldGenerateDimensionCountCandidate() {
        when(dynamicLogQueryService.getTableName("sink-1")).thenReturn("syslog_logs");

        Optional<SqlCandidate> candidate = provider.generate(context, "按 severity 统计最近24小时数量");

        assertThat(candidate).isPresent();
        assertThat(candidate.get().source()).isEqualTo("template");
        assertThat(candidate.get().resultType()).isEqualTo("category");
        assertThat(candidate.get().sql()).contains("SELECT `severity` AS `severity`, count() AS count");
        assertThat(candidate.get().sql()).contains("GROUP BY `severity`");
        assertThat(candidate.get().sql()).contains("ORDER BY count DESC LIMIT 10");
    }

    @Test
    void shouldSupportOnlyClickHouseHighFrequencyQueries() {
        AgentExecutionContext postgres = new AgentExecutionContext("sink-1", "pg", "postgresql", 1L, "session-1");

        assertThat(provider.supports(context, "查最近一小时的日志数据总数")).isTrue();
        assertThat(provider.supports(context, "按 severity 统计最近24小时数量")).isTrue();
        assertThat(provider.supports(context, "查询 message 包含 error 的明细")).isFalse();
        assertThat(provider.supports(postgres, "查最近一小时的日志数据总数")).isFalse();
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```bash
cd /Users/xsw/custom_idea_project/qqwer/log-analysis-backend
mvn -pl log-analysis-app -Dtest=TemplateSqlCandidateProviderTest test
```

Expected: FAIL，因为旧测试仍存在或模板候选类/方法尚未满足新测试。

- [ ] **Step 3: 实现或整理模板候选最小代码**

Ensure `SqlCandidate.java`:

```java
package cn.mw.loganalysis.agent.service;

import lombok.Builder;

import java.util.Map;

/**
 * Text2SQL 候选 SQL。
 */
@Builder
record SqlCandidate(String source,
                    String sql,
                    String resultType,
                    double confidence,
                    long generationTimeMs,
                    Map<String, Object> metadata) {
}
```

Ensure `SqlCandidateProvider.java`:

```java
package cn.mw.loganalysis.agent.service;

import org.springframework.core.Ordered;

import java.util.Optional;

/**
 * Text2SQL 候选来源策略。
 */
interface SqlCandidateProvider extends Ordered {

    /**
     * 返回候选来源标识，例如 template、history、llm。
     */
    String source();

    /**
     * 判断当前查询是否适合由该候选来源处理。
     */
    boolean supports(AgentExecutionContext context, String query);

    /**
     * 生成候选 SQL；不负责校验、执行和落库。
     */
    Optional<SqlCandidate> generate(AgentExecutionContext context, String query);

    @Override
    default int getOrder() {
        return 0;
    }
}
```

Ensure `SqlTemplateSupport.java`:

```java
package cn.mw.loganalysis.agent.service;

import cn.mw.loganalysis.common.util.DateTimeUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * Text2SQL 模板候选辅助方法。
 */
final class SqlTemplateSupport {

    private SqlTemplateSupport() {
    }

    static String quoteIdentifier(String value) {
        return "`" + StringUtils.replace(StringUtils.defaultString(value), "`", "") + "`";
    }

    static String literal(String value) {
        return "'" + StringUtils.replace(StringUtils.defaultString(value), "'", "''") + "'";
    }

    static String timeWhere(AgentTimeWindow timeWindow) {
        return " WHERE `timestamp` >= " + literal(DateTimeUtils.format(timeWindow.start()))
                + " AND `timestamp` <= " + literal(DateTimeUtils.format(timeWindow.end()));
    }

    static String severityClause(String severity) {
        if (StringUtils.isBlank(severity)) {
            return "";
        }
        return " AND `severity` IN (" + String.join(", ", resolveSeverityValues(severity).stream()
                .map(SqlTemplateSupport::literal)
                .toList()) + ")";
    }

    static List<String> resolveSeverityValues(String severity) {
        String normalized = StringUtils.lowerCase(severity, Locale.ROOT);
        if (AgentToolSupport.containsAny(normalized, "warn", "告警", "警告")) {
            return List.of("warning", "warn", "WARN", "WARNING");
        }
        if (AgentToolSupport.containsAny(normalized, "error", "错误", "异常")) {
            return List.of("error", "ERROR");
        }
        if (AgentToolSupport.containsAny(normalized, "info", "信息")) {
            return List.of("info", "INFO");
        }
        if (AgentToolSupport.containsAny(normalized, "debug", "调试")) {
            return List.of("debug", "DEBUG");
        }
        return List.of(severity);
    }

    static String resolveDimension(String query) {
        String lower = StringUtils.lowerCase(AgentToolSupport.normalizeText(query), Locale.ROOT);
        if (AgentToolSupport.containsAny(lower, "severity", "level", "级别", "等级")) {
            return "severity";
        }
        if (AgentToolSupport.containsAny(lower, "hostname", "host", "主机")) {
            return "hostname";
        }
        if (AgentToolSupport.containsAny(lower, "appname", "service", "服务", "应用")) {
            return "appname";
        }
        if (AgentToolSupport.containsAny(lower, "facility")) {
            return "facility";
        }
        if (AgentToolSupport.containsAny(lower, "source_type", "来源类型")) {
            return "source_type";
        }
        return null;
    }
}
```

Ensure `TemplateSqlCandidateProvider.java`:

```java
package cn.mw.loganalysis.agent.service;

import cn.mw.loganalysis.stats.service.DynamicLogQueryService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 基于规则模板生成高频统计 SQL 候选。
 */
@Component
@RequiredArgsConstructor
public class TemplateSqlCandidateProvider implements SqlCandidateProvider {

    private final DynamicLogQueryService dynamicLogQueryService;

    @Override
    public int getOrder() {
        return 10;
    }

    @Override
    public String source() {
        return "template";
    }

    /**
     * 仅处理 ClickHouse 高频统计问题。
     */
    @Override
    public boolean supports(AgentExecutionContext context, String query) {
        return context != null
                && StringUtils.equalsIgnoreCase(context.datasourceType(), "clickhouse")
                && (isSimpleCount(query) || isDimensionCount(query));
    }

    /**
     * 生成模板 SQL 候选。
     */
    @Override
    public Optional<SqlCandidate> generate(AgentExecutionContext context, String query) {
        if (!supports(context, query)) {
            return Optional.empty();
        }
        long startedAt = System.currentTimeMillis();
        AgentTimeWindow timeWindow = AgentToolSupport.resolveTimeWindow(query, false);
        String tableName = dynamicLogQueryService.getTableName(context.datasourceId());
        String sql;
        String resultType;
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("timeRange", timeWindow.label());

        if (isDimensionCount(query)) {
            String dimension = SqlTemplateSupport.resolveDimension(query);
            if (StringUtils.isBlank(dimension)) {
                return Optional.empty();
            }
            sql = "SELECT " + SqlTemplateSupport.quoteIdentifier(dimension) + " AS " + SqlTemplateSupport.quoteIdentifier(dimension)
                    + ", count() AS count FROM " + SqlTemplateSupport.quoteIdentifier(tableName)
                    + SqlTemplateSupport.timeWhere(timeWindow)
                    + " GROUP BY " + SqlTemplateSupport.quoteIdentifier(dimension)
                    + " ORDER BY count DESC LIMIT 10";
            resultType = "category";
            metadata.put("dimension", dimension);
        } else {
            String severity = AgentIntentTextSupport.extractSeverity(query);
            sql = "SELECT count() AS total FROM " + SqlTemplateSupport.quoteIdentifier(tableName)
                    + SqlTemplateSupport.timeWhere(timeWindow)
                    + SqlTemplateSupport.severityClause(severity);
            resultType = "metric";
            if (StringUtils.isNotBlank(severity)) {
                metadata.put("severity", severity);
            }
        }

        return Optional.of(SqlCandidate.builder()
                .source(source())
                .sql(sql)
                .resultType(resultType)
                .confidence(0.95D)
                .generationTimeMs(System.currentTimeMillis() - startedAt)
                .metadata(metadata)
                .build());
    }

    private boolean isSimpleCount(String query) {
        String lower = StringUtils.lowerCase(AgentToolSupport.normalizeText(query), Locale.ROOT);
        boolean countRequest = AgentToolSupport.containsAny(lower, "总数", "多少条", "多少", "数量", "条数", "count");
        boolean complexAggregation = AgentToolSupport.containsAny(lower,
                "按", "分组", "排行", "top", "占比", "平均", "avg", "sum", "max", "min", "每小时", "每分钟", "趋势", "时序");
        return countRequest && !complexAggregation;
    }

    private boolean isDimensionCount(String query) {
        String lower = StringUtils.lowerCase(AgentToolSupport.normalizeText(query), Locale.ROOT);
        return AgentToolSupport.containsAny(lower, "按", "分组")
                && AgentToolSupport.containsAny(lower, "统计", "数量", "总数", "条数", "count")
                && StringUtils.isNotBlank(SqlTemplateSupport.resolveDimension(query));
    }
}
```

Remove old tests:

```bash
rm log-analysis-backend/log-analysis-app/src/test/java/cn/mw/loganalysis/agent/service/LogCountMetricPreflightStrategyTest.java
rm log-analysis-backend/log-analysis-app/src/test/java/cn/mw/loganalysis/agent/service/DimensionCountPreflightStrategyTest.java
```

- [ ] **Step 4: 运行测试确认通过**

Run:

```bash
cd /Users/xsw/custom_idea_project/qqwer/log-analysis-backend
mvn -pl log-analysis-app -Dtest=TemplateSqlCandidateProviderTest test
```

Expected: PASS.

- [ ] **Step 5: 提交任务 1**

```bash
cd /Users/xsw/custom_idea_project/qqwer
git add log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/SqlCandidate.java \
  log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/SqlCandidateProvider.java \
  log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/SqlTemplateSupport.java \
  log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/TemplateSqlCandidateProvider.java \
  log-analysis-backend/log-analysis-app/src/test/java/cn/mw/loganalysis/agent/service/TemplateSqlCandidateProviderTest.java \
  log-analysis-backend/log-analysis-app/src/test/java/cn/mw/loganalysis/agent/service/LogCountMetricPreflightStrategyTest.java \
  log-analysis-backend/log-analysis-app/src/test/java/cn/mw/loganalysis/agent/service/DimensionCountPreflightStrategyTest.java
git commit -m "feat: add text2sql template candidate provider"
```

## Task 2: 历史查询经验库和历史候选 Provider

**Files:**

- Create/Modify: `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/entity/AgentSqlQueryExample.java`
- Create/Modify: `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/mapper/AgentSqlQueryExampleMapper.java`
- Create/Modify: `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/repository/AgentSqlQueryExampleRepository.java`
- Create/Modify: `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/SqlQuestionNormalizer.java`
- Create/Modify: `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/HistorySqlCandidateProvider.java`
- Create/Modify: `log-analysis-backend/log-analysis-app/src/main/resources/db/changelog/changes/20260528-agent-sql-query-examples.sql`
- Modify: `log-analysis-backend/log-analysis-app/src/main/resources/db/changelog/db.changelog-master.yaml`
- Test: `log-analysis-backend/log-analysis-app/src/test/java/cn/mw/loganalysis/agent/service/HistorySqlCandidateProviderTest.java`

- [ ] **Step 1: 写历史候选失败测试**

Create `log-analysis-backend/log-analysis-app/src/test/java/cn/mw/loganalysis/agent/service/HistorySqlCandidateProviderTest.java`:

```java
package cn.mw.loganalysis.agent.service;

import cn.mw.loganalysis.agent.entity.AgentSqlQueryExample;
import cn.mw.loganalysis.agent.repository.AgentSqlQueryExampleRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HistorySqlCandidateProviderTest {

    private final AgentSqlQueryExampleRepository repository = mock(AgentSqlQueryExampleRepository.class);
    private final SqlQuestionNormalizer normalizer = new SqlQuestionNormalizer();
    private final HistorySqlCandidateProvider provider = new HistorySqlCandidateProvider(repository, normalizer);
    private final AgentExecutionContext context = new AgentExecutionContext(
            "sink-1",
            "syslog_logs_sink",
            "clickhouse",
            1L,
            "session-1"
    );

    @Test
    void shouldReturnSimilarHistoricalCandidate() {
        AgentSqlQueryExample example = AgentSqlQueryExample.builder()
                .id(11L)
                .userId(1L)
                .datasourceId("sink-1")
                .normalizedQuestion(normalizer.normalize("按 severity 统计最近24小时数量"))
                .sqlTemplate("SELECT `severity`, count() AS count FROM `syslog_logs` GROUP BY `severity` LIMIT 10")
                .resultType("category")
                .build();
        when(repository.findRecent(1L, "sink-1")).thenReturn(List.of(example));

        Optional<SqlCandidate> candidate = provider.generate(context, "按 severity 统计最近1小时数量");

        assertThat(candidate).isPresent();
        assertThat(candidate.get().source()).isEqualTo("history");
        assertThat(candidate.get().sql()).contains("GROUP BY `severity`");
        assertThat(candidate.get().metadata()).containsEntry("exampleId", 11L);
    }

    @Test
    void shouldReturnEmptyWhenSimilarityIsLow() {
        AgentSqlQueryExample example = AgentSqlQueryExample.builder()
                .id(12L)
                .userId(1L)
                .datasourceId("sink-1")
                .normalizedQuestion(normalizer.normalize("按主机统计日志数量"))
                .sqlTemplate("SELECT `hostname`, count() AS count FROM `syslog_logs` GROUP BY `hostname` LIMIT 10")
                .resultType("category")
                .build();
        when(repository.findRecent(1L, "sink-1")).thenReturn(List.of(example));

        Optional<SqlCandidate> candidate = provider.generate(context, "查询 message 包含 timeout 的明细");

        assertThat(candidate).isEmpty();
    }

    @Test
    void shouldRequireUserAndDatasource() {
        AgentExecutionContext anonymous = new AgentExecutionContext("sink-1", "sink", "clickhouse", null, "session-1");
        AgentExecutionContext noDatasource = new AgentExecutionContext("", "sink", "clickhouse", 1L, "session-1");

        assertThat(provider.supports(context, "按 severity 统计")).isTrue();
        assertThat(provider.supports(anonymous, "按 severity 统计")).isFalse();
        assertThat(provider.supports(noDatasource, "按 severity 统计")).isFalse();
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```bash
cd /Users/xsw/custom_idea_project/qqwer/log-analysis-backend
mvn -pl log-analysis-app -Dtest=HistorySqlCandidateProviderTest test
```

Expected: FAIL，因为实体、仓储、归一化或 Provider 尚未完整。

- [ ] **Step 3: 实现历史候选最小代码**

Ensure `AgentSqlQueryExample.java`:

```java
package cn.mw.loganalysis.agent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 智能助手成功 SQL 查询经验。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("agent_sql_query_examples")
public class AgentSqlQueryExample {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String datasourceId;

    private String datasourceType;

    private String question;

    private String normalizedQuestion;

    private String sqlTemplate;

    private String resultType;

    private Integer hitCount;

    private LocalDateTime lastUsedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
```

Ensure `AgentSqlQueryExampleMapper.java`:

```java
package cn.mw.loganalysis.agent.mapper;

import cn.mw.loganalysis.agent.entity.AgentSqlQueryExample;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 智能助手 SQL 查询经验 Mapper。
 */
@Mapper
@DS("postgres")
public interface AgentSqlQueryExampleMapper extends BaseMapper<AgentSqlQueryExample> {
}
```

Ensure `AgentSqlQueryExampleRepository.java`:

```java
package cn.mw.loganalysis.agent.repository;

import cn.mw.loganalysis.agent.entity.AgentSqlQueryExample;
import cn.mw.loganalysis.agent.mapper.AgentSqlQueryExampleMapper;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 智能助手 SQL 查询经验仓储。
 */
@Repository
@DS("postgres")
@RequiredArgsConstructor
public class AgentSqlQueryExampleRepository {

    private static final long MAX_RECENT_EXAMPLES = 50L;

    private final AgentSqlQueryExampleMapper exampleMapper;

    /**
     * 查询当前用户和数据源最近成功的 SQL 经验。
     */
    public List<AgentSqlQueryExample> findRecent(Long userId, String datasourceId) {
        if (ObjectUtils.isEmpty(userId) || StringUtils.isBlank(datasourceId)) {
            return Collections.emptyList();
        }
        Page<AgentSqlQueryExample> page = new Page<>(1, MAX_RECENT_EXAMPLES, false);
        return exampleMapper.selectPage(
                page,
                Wrappers.<AgentSqlQueryExample>lambdaQuery()
                        .eq(AgentSqlQueryExample::getUserId, userId)
                        .eq(AgentSqlQueryExample::getDatasourceId, StringUtils.trim(datasourceId))
                        .orderByDesc(AgentSqlQueryExample::getUpdatedAt)
        ).getRecords();
    }

    /**
     * 保存成功 SQL 经验，重复问题更新 SQL 和时间。
     */
    public void saveSuccess(Long userId,
                            String datasourceId,
                            String datasourceType,
                            String question,
                            String normalizedQuestion,
                            String sql,
                            String resultType) {
        if (ObjectUtils.isEmpty(userId) || StringUtils.isAnyBlank(datasourceId, normalizedQuestion, sql)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        AgentSqlQueryExample existing = exampleMapper.selectOne(
                Wrappers.<AgentSqlQueryExample>lambdaQuery()
                        .eq(AgentSqlQueryExample::getUserId, userId)
                        .eq(AgentSqlQueryExample::getDatasourceId, datasourceId)
                        .eq(AgentSqlQueryExample::getNormalizedQuestion, normalizedQuestion)
                        .last("LIMIT 1")
        );
        if (existing == null) {
            exampleMapper.insert(AgentSqlQueryExample.builder()
                    .userId(userId)
                    .datasourceId(datasourceId)
                    .datasourceType(datasourceType)
                    .question(question)
                    .normalizedQuestion(normalizedQuestion)
                    .sqlTemplate(sql)
                    .resultType(resultType)
                    .hitCount(0)
                    .lastUsedAt(now)
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
            return;
        }

        exampleMapper.update(
                null,
                Wrappers.<AgentSqlQueryExample>lambdaUpdate()
                        .eq(AgentSqlQueryExample::getId, existing.getId())
                        .set(AgentSqlQueryExample::getQuestion, question)
                        .set(AgentSqlQueryExample::getSqlTemplate, sql)
                        .set(AgentSqlQueryExample::getResultType, resultType)
                        .set(AgentSqlQueryExample::getLastUsedAt, now)
                        .set(AgentSqlQueryExample::getUpdatedAt, now)
        );
    }

    /**
     * 记录历史候选被再次采用。
     */
    public void markUsed(Long id) {
        if (id == null) {
            return;
        }
        AgentSqlQueryExample existing = exampleMapper.selectById(id);
        if (existing == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        exampleMapper.update(
                null,
                Wrappers.<AgentSqlQueryExample>lambdaUpdate()
                        .eq(AgentSqlQueryExample::getId, id)
                        .set(AgentSqlQueryExample::getHitCount, ObjectUtils.defaultIfNull(existing.getHitCount(), 0) + 1)
                        .set(AgentSqlQueryExample::getLastUsedAt, now)
                        .set(AgentSqlQueryExample::getUpdatedAt, now)
        );
    }
}
```

Ensure `SqlQuestionNormalizer.java`:

```java
package cn.mw.loganalysis.agent.service;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Text2SQL 问题归一化与轻量相似度计算。
 */
@Component
public class SqlQuestionNormalizer {

    /**
     * 去掉时间范围的具体数字，保留查询意图和维度词。
     */
    public String normalize(String question) {
        return StringUtils.lowerCase(AgentToolSupport.normalizeText(question), Locale.ROOT)
                .replaceAll("最近\\s*[0-9一二两三四五六七八九十半]+\\s*(分钟|小时|天|周)", "最近<range>")
                .replaceAll("近\\s*[0-9一二两三四五六七八九十半]+\\s*(分钟|小时|天|周)", "最近<range>")
                .replaceAll("\\d+", "<num>")
                .replaceAll("[，。！？、,.!?;；:：\"“”'`]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * 使用 token Jaccard 相似度做 v1 轻量匹配。
     */
    public double similarity(String left, String right) {
        Set<String> leftTokens = tokens(left);
        Set<String> rightTokens = tokens(right);
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) {
            return 0D;
        }
        Set<String> intersection = new LinkedHashSet<>(leftTokens);
        intersection.retainAll(rightTokens);
        Set<String> union = new LinkedHashSet<>(leftTokens);
        union.addAll(rightTokens);
        return union.isEmpty() ? 0D : (double) intersection.size() / union.size();
    }

    private Set<String> tokens(String text) {
        return Arrays.stream(StringUtils.defaultString(text).split("\\s+"))
                .map(String::trim)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
```

Ensure `HistorySqlCandidateProvider.java`:

```java
package cn.mw.loganalysis.agent.service;

import cn.mw.loganalysis.agent.entity.AgentSqlQueryExample;
import cn.mw.loganalysis.agent.repository.AgentSqlQueryExampleRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 从历史成功 SQL 经验中生成候选。
 */
@Component
@RequiredArgsConstructor
public class HistorySqlCandidateProvider implements SqlCandidateProvider {

    private static final double MIN_SIMILARITY = 0.58D;

    private final AgentSqlQueryExampleRepository exampleRepository;
    private final SqlQuestionNormalizer questionNormalizer;

    @Override
    public int getOrder() {
        return 20;
    }

    @Override
    public String source() {
        return "history";
    }

    /**
     * 历史候选需要登录用户和数据源。
     */
    @Override
    public boolean supports(AgentExecutionContext context, String query) {
        return context != null
                && context.userId() != null
                && StringUtils.isNotBlank(context.datasourceId())
                && StringUtils.isNotBlank(query);
    }

    /**
     * 查找最相似的成功 SQL 模板。
     */
    @Override
    public Optional<SqlCandidate> generate(AgentExecutionContext context, String query) {
        if (!supports(context, query)) {
            return Optional.empty();
        }
        long startedAt = System.currentTimeMillis();
        String normalized = questionNormalizer.normalize(query);
        return exampleRepository.findRecent(context.userId(), context.datasourceId()).stream()
                .map(example -> new ScoredExample(example, questionNormalizer.similarity(normalized, example.getNormalizedQuestion())))
                .filter(scored -> scored.score() >= MIN_SIMILARITY)
                .max(Comparator.comparingDouble(ScoredExample::score))
                .map(scored -> buildCandidate(scored, startedAt));
    }

    /**
     * 将历史记录转成候选 SQL。
     */
    private SqlCandidate buildCandidate(ScoredExample scored, long startedAt) {
        AgentSqlQueryExample example = scored.example();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("exampleId", example.getId());
        metadata.put("similarity", scored.score());
        metadata.put("normalizedQuestion", example.getNormalizedQuestion());
        return SqlCandidate.builder()
                .source(source())
                .sql(example.getSqlTemplate())
                .resultType(StringUtils.defaultIfBlank(example.getResultType(), "list"))
                .confidence(scored.score())
                .generationTimeMs(System.currentTimeMillis() - startedAt)
                .metadata(metadata)
                .build();
    }

    private record ScoredExample(AgentSqlQueryExample example, double score) {
    }
}
```

Ensure `20260528-agent-sql-query-examples.sql`:

```sql
--liquibase formatted sql

--changeset codex:20260528-01-create-agent-sql-query-examples
CREATE TABLE IF NOT EXISTS agent_sql_query_examples (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    datasource_id VARCHAR(36) NOT NULL,
    datasource_type VARCHAR(50) NOT NULL,
    question TEXT NOT NULL,
    normalized_question TEXT NOT NULL,
    sql_template TEXT NOT NULL,
    result_type VARCHAR(50),
    hit_count INTEGER NOT NULL DEFAULT 0,
    last_used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

--changeset codex:20260528-02-index-agent-sql-query-examples-lookup
CREATE INDEX IF NOT EXISTS idx_agent_sql_query_examples_lookup
ON agent_sql_query_examples(user_id, datasource_id, updated_at DESC);

--changeset codex:20260528-03-index-agent-sql-query-examples-normalized
CREATE INDEX IF NOT EXISTS idx_agent_sql_query_examples_normalized
ON agent_sql_query_examples(datasource_id, normalized_question);
```

Ensure `db.changelog-master.yaml` contains:

```yaml
  - include:
      file: db/changelog/changes/20260528-agent-sql-query-examples.sql
```

- [ ] **Step 4: 运行测试确认通过**

Run:

```bash
cd /Users/xsw/custom_idea_project/qqwer/log-analysis-backend
mvn -pl log-analysis-app -Dtest=HistorySqlCandidateProviderTest test
```

Expected: PASS.

- [ ] **Step 5: 提交任务 2**

```bash
cd /Users/xsw/custom_idea_project/qqwer
git add log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/entity/AgentSqlQueryExample.java \
  log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/mapper/AgentSqlQueryExampleMapper.java \
  log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/repository/AgentSqlQueryExampleRepository.java \
  log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/SqlQuestionNormalizer.java \
  log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/HistorySqlCandidateProvider.java \
  log-analysis-backend/log-analysis-app/src/main/resources/db/changelog/changes/20260528-agent-sql-query-examples.sql \
  log-analysis-backend/log-analysis-app/src/main/resources/db/changelog/db.changelog-master.yaml \
  log-analysis-backend/log-analysis-app/src/test/java/cn/mw/loganalysis/agent/service/HistorySqlCandidateProviderTest.java
git commit -m "feat: add text2sql history candidate provider"
```

## Task 3: SQL 候选安全校验器

**Files:**

- Create/Modify: `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/SqlCandidateValidationResult.java`
- Create/Modify: `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/SqlCandidateValidator.java`
- Test: `log-analysis-backend/log-analysis-app/src/test/java/cn/mw/loganalysis/agent/service/SqlCandidateValidatorTest.java`

- [ ] **Step 1: 写校验器失败测试**

Create `log-analysis-backend/log-analysis-app/src/test/java/cn/mw/loganalysis/agent/service/SqlCandidateValidatorTest.java`:

```java
package cn.mw.loganalysis.agent.service;

import cn.mw.loganalysis.stats.service.DynamicLogQueryService;
import cn.mw.loganalysis.stats.service.query.FieldInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SqlCandidateValidatorTest {

    private final DynamicLogQueryService dynamicLogQueryService = mock(DynamicLogQueryService.class);
    private final SqlCandidateValidator validator = new SqlCandidateValidator(dynamicLogQueryService);
    private final AgentExecutionContext context = new AgentExecutionContext(
            "sink-1",
            "syslog_logs_sink",
            "clickhouse",
            1L,
            "session-1"
    );

    @BeforeEach
    void setUp() {
        when(dynamicLogQueryService.getTableName("sink-1")).thenReturn("syslog_logs");
        when(dynamicLogQueryService.getTableSchema("sink-1")).thenReturn(List.of(
                FieldInfo.builder().name("timestamp").type("DateTime").build(),
                FieldInfo.builder().name("severity").type("String").build(),
                FieldInfo.builder().name("message").type("String").build()
        ));
    }

    @Test
    void shouldAcceptSafeSelectAndAppendLimit() {
        SqlCandidate candidate = candidate("SELECT count() AS total FROM `syslog_logs` WHERE `severity` = 'ERROR'");

        SqlCandidateValidationResult result = validator.validate(context, candidate);

        assertThat(result.valid()).isTrue();
        assertThat(result.sql()).endsWith("LIMIT 200");
    }

    @Test
    void shouldRejectDangerousSql() {
        SqlCandidateValidationResult result = validator.validate(context, candidate("DELETE FROM `syslog_logs`"));

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).contains("只允许");
    }

    @Test
    void shouldRejectCrossTableSql() {
        SqlCandidateValidationResult result = validator.validate(context, candidate("SELECT * FROM `other_logs` LIMIT 10"));

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).contains("当前数据源");
    }

    @Test
    void shouldRejectUnknownBacktickedField() {
        SqlCandidateValidationResult result = validator.validate(context, candidate("SELECT `unknown_field` FROM `syslog_logs` LIMIT 10"));

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).contains("不存在");
    }

    @Test
    void shouldKeepExistingLimit() {
        SqlCandidateValidationResult result = validator.validate(context, candidate("SELECT `severity` FROM `syslog_logs` LIMIT 10"));

        assertThat(result.valid()).isTrue();
        assertThat(result.sql()).endsWith("LIMIT 10");
    }

    private SqlCandidate candidate(String sql) {
        return SqlCandidate.builder()
                .source("test")
                .sql(sql)
                .resultType("list")
                .confidence(1D)
                .metadata(Map.of())
                .build();
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```bash
cd /Users/xsw/custom_idea_project/qqwer/log-analysis-backend
mvn -pl log-analysis-app -Dtest=SqlCandidateValidatorTest test
```

Expected: FAIL，因为校验器尚未完整或错误消息不匹配。

- [ ] **Step 3: 实现校验器最小代码**

Ensure `SqlCandidateValidationResult.java`:

```java
package cn.mw.loganalysis.agent.service;

/**
 * SQL 候选校验结果。
 */
record SqlCandidateValidationResult(boolean valid, String sql, String reason) {

    static SqlCandidateValidationResult valid(String sql) {
        return new SqlCandidateValidationResult(true, sql, null);
    }

    static SqlCandidateValidationResult invalid(String reason) {
        return new SqlCandidateValidationResult(false, null, reason);
    }
}
```

Ensure `SqlCandidateValidator.java`:

```java
package cn.mw.loganalysis.agent.service;

import cn.mw.loganalysis.stats.service.DynamicLogQueryService;
import cn.mw.loganalysis.stats.service.query.FieldInfo;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Text2SQL 候选 SQL 安全校验。
 */
@Component
@RequiredArgsConstructor
public class SqlCandidateValidator {

    private static final Pattern FORBIDDEN_SQL_PATTERN = Pattern.compile(
            "\\b(insert|update|delete|drop|alter|truncate|create|replace|rename|grant|revoke|attach|detach|optimize|system|kill)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TABLE_PATTERN = Pattern.compile("\\b(?:from|join)\\s+`?([A-Za-z0-9_\\.]+)`?", Pattern.CASE_INSENSITIVE);
    private static final Pattern LIMIT_PATTERN = Pattern.compile("\\blimit\\b", Pattern.CASE_INSENSITIVE);
    private static final int DEFAULT_LIMIT = 200;

    private final DynamicLogQueryService dynamicLogQueryService;

    /**
     * 校验候选 SQL，并在缺少 LIMIT 时补默认限制。
     */
    public SqlCandidateValidationResult validate(AgentExecutionContext context, SqlCandidate candidate) {
        if (candidate == null || StringUtils.isBlank(candidate.sql())) {
            return SqlCandidateValidationResult.invalid("候选 SQL 为空");
        }
        String sql = stripTrailingSemicolon(candidate.sql());
        String normalized = sql.toLowerCase(Locale.ROOT);
        if (!(normalized.startsWith("select") || normalized.startsWith("with"))) {
            return SqlCandidateValidationResult.invalid("只允许 SELECT/WITH 查询");
        }
        if (FORBIDDEN_SQL_PATTERN.matcher(sql).find()) {
            return SqlCandidateValidationResult.invalid("SQL 包含禁止的写入或 DDL 关键字");
        }
        String tableName = dynamicLogQueryService.getTableName(context.datasourceId());
        if (!referencesOnlyCurrentTable(sql, tableName)) {
            return SqlCandidateValidationResult.invalid("SQL 查询表不属于当前数据源");
        }
        if (!usesKnownFields(context, sql, tableName)) {
            return SqlCandidateValidationResult.invalid("SQL 包含当前表不存在的字段");
        }
        return SqlCandidateValidationResult.valid(ensureLimit(sql));
    }

    /**
     * 去掉尾部分号，避免追加 LIMIT 时产生多语句。
     */
    private String stripTrailingSemicolon(String sql) {
        return StringUtils.removeEnd(StringUtils.trim(sql), ";");
    }

    /**
     * SQL 中所有 FROM/JOIN 表都必须指向当前数据源表。
     */
    private boolean referencesOnlyCurrentTable(String sql, String tableName) {
        Matcher matcher = TABLE_PATTERN.matcher(sql);
        boolean matched = false;
        while (matcher.find()) {
            matched = true;
            String referenced = unquote(lastNamePart(matcher.group(1)));
            if (!StringUtils.equalsIgnoreCase(referenced, tableName)) {
                return false;
            }
        }
        return matched;
    }

    /**
     * 轻量字段校验：只校验反引号字段，避免误伤函数别名和字符串字面量。
     */
    private boolean usesKnownFields(AgentExecutionContext context, String sql, String tableName) {
        List<FieldInfo> schema = dynamicLogQueryService.getTableSchema(context.datasourceId());
        if (CollectionUtils.isEmpty(schema)) {
            return true;
        }
        Set<String> fields = schema.stream()
                .map(FieldInfo::getName)
                .filter(StringUtils::isNotBlank)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        fields.add(tableName.toLowerCase(Locale.ROOT));

        Matcher matcher = Pattern.compile("`([^`]+)`").matcher(sql);
        while (matcher.find()) {
            String token = lastNamePart(matcher.group(1)).toLowerCase(Locale.ROOT);
            if (!fields.contains(token)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 缺少 LIMIT 时自动补默认限制，避免大结果拖慢助手。
     */
    private String ensureLimit(String sql) {
        if (LIMIT_PATTERN.matcher(sql).find()) {
            return sql;
        }
        return sql + " LIMIT " + DEFAULT_LIMIT;
    }

    private String lastNamePart(String value) {
        String normalized = StringUtils.defaultString(value);
        int dot = normalized.lastIndexOf('.');
        return dot >= 0 ? normalized.substring(dot + 1) : normalized;
    }

    private String unquote(String value) {
        return StringUtils.removeEnd(StringUtils.removeStart(value, "`"), "`");
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run:

```bash
cd /Users/xsw/custom_idea_project/qqwer/log-analysis-backend
mvn -pl log-analysis-app -Dtest=SqlCandidateValidatorTest test
```

Expected: PASS.

- [ ] **Step 5: 提交任务 3**

```bash
cd /Users/xsw/custom_idea_project/qqwer
git add log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/SqlCandidateValidationResult.java \
  log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/SqlCandidateValidator.java \
  log-analysis-backend/log-analysis-app/src/test/java/cn/mw/loganalysis/agent/service/SqlCandidateValidatorTest.java
git commit -m "feat: validate text2sql sql candidates"
```

## Task 4: LLM 只生成 SQL 候选

**Files:**

- Modify: `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/stats/service/AiQueryService.java`
- Create/Modify: `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/LlmSqlCandidateProvider.java`

- [ ] **Step 1: 写 AiQueryService SQL-only 测试**

Create `log-analysis-backend/log-analysis-app/src/test/java/cn/mw/loganalysis/agent/service/LlmSqlCandidateProviderTest.java`:

```java
package cn.mw.loganalysis.agent.service;

import cn.mw.loganalysis.stats.dto.AiQueryResponse;
import cn.mw.loganalysis.stats.service.AiQueryService;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmSqlCandidateProviderTest {

    private final AiQueryService aiQueryService = mock(AiQueryService.class);
    private final LlmSqlCandidateProvider provider = new LlmSqlCandidateProvider(aiQueryService);
    private final AgentExecutionContext context = new AgentExecutionContext(
            "sink-1",
            "syslog_logs_sink",
            "clickhouse",
            1L,
            "session-1"
    );

    @Test
    void shouldGenerateCandidateWithSqlOnlyApi() {
        when(aiQueryService.generateSqlOnly(org.mockito.ArgumentMatchers.any()))
                .thenReturn(AiQueryResponse.builder()
                        .success(true)
                        .sql("SELECT * FROM `syslog_logs` LIMIT 10")
                        .sqlGenerationTime(0.42D)
                        .build());

        Optional<SqlCandidate> candidate = provider.generate(context, "查最近十条日志");

        assertThat(candidate).isPresent();
        assertThat(candidate.get().source()).isEqualTo("llm");
        assertThat(candidate.get().sql()).isEqualTo("SELECT * FROM `syslog_logs` LIMIT 10");
        assertThat(candidate.get().metadata()).containsEntry("sqlGenerationTime", 0.42D);
        verify(aiQueryService).generateSqlOnly(org.mockito.ArgumentMatchers.argThat(request ->
                "查最近十条日志".equals(request.getQuery()) && "sink-1".equals(request.getDatasourceId())
        ));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```bash
cd /Users/xsw/custom_idea_project/qqwer/log-analysis-backend
mvn -pl log-analysis-app -Dtest=LlmSqlCandidateProviderTest test
```

Expected: FAIL，如果 `generateSqlOnly` 或 `LlmSqlCandidateProvider` 尚未完整。

- [ ] **Step 3: 实现 SQL-only 和 LLM Provider**

In `AiQueryService.java`:

- Add public method `generateSqlOnly(AiQueryRequest request)`.
- Refactor single datasource query so SQL generation and SQL execution are separated.
- Keep `query(AiQueryRequest)` behavior unchanged for existing callers.

Required method shape:

```java
/**
 * 只调用 AI service 生成 SQL，不执行 SQL。
 */
public AiQueryResponse generateSqlOnly(AiQueryRequest request) {
    long startTime = System.currentTimeMillis();
    log.info("生成AI SQL候选: {}, datasourceId: {}", request.getQuery(), request.getDatasourceId());
    try {
        if (request.getDatasourceIds() != null && !request.getDatasourceIds().isEmpty()) {
            return buildErrorResponse("候选竞争暂不支持多数据源 SQL 生成", startTime);
        }
        return generateSingleDatasourceSql(request, startTime);
    } catch (Exception e) {
        log.error("AI SQL候选生成失败", e);
        return buildErrorResponse("SQL生成失败: " + e.getMessage(), startTime);
    }
}
```

Ensure `LlmSqlCandidateProvider.java`:

```java
package cn.mw.loganalysis.agent.service;

import cn.mw.loganalysis.stats.dto.AiQueryRequest;
import cn.mw.loganalysis.stats.dto.AiQueryResponse;
import cn.mw.loganalysis.stats.service.AiQueryService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 调用 AI service 生成 LLM SQL 候选。
 */
@Component
@RequiredArgsConstructor
public class LlmSqlCandidateProvider implements SqlCandidateProvider {

    private final AiQueryService aiQueryService;

    @Override
    public int getOrder() {
        return 100;
    }

    @Override
    public String source() {
        return "llm";
    }

    @Override
    public boolean supports(AgentExecutionContext context, String query) {
        return context != null && StringUtils.isNotBlank(query);
    }

    /**
     * 只使用 AI service 生成 SQL，不在这里执行最终查询。
     */
    @Override
    public Optional<SqlCandidate> generate(AgentExecutionContext context, String query) {
        if (!supports(context, query)) {
            return Optional.empty();
        }
        long startedAt = System.currentTimeMillis();
        AiQueryRequest request = new AiQueryRequest();
        request.setQuery(StringUtils.defaultString(StringUtils.trimToNull(query)));
        request.setDatasourceId(context.datasourceId());

        AiQueryResponse response = aiQueryService.generateSqlOnly(request);
        if (!Boolean.TRUE.equals(response.getSuccess()) || StringUtils.isBlank(response.getSql())) {
            throw new IllegalStateException(StringUtils.defaultIfBlank(response.getError(), "LLM SQL 生成失败"));
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("sqlGenerationTime", response.getSqlGenerationTime());
        return Optional.of(SqlCandidate.builder()
                .source(source())
                .sql(response.getSql())
                .resultType("list")
                .confidence(0.7D)
                .generationTimeMs(System.currentTimeMillis() - startedAt)
                .metadata(metadata)
                .build());
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run:

```bash
cd /Users/xsw/custom_idea_project/qqwer/log-analysis-backend
mvn -pl log-analysis-app -Dtest=LlmSqlCandidateProviderTest test
```

Expected: PASS.

- [ ] **Step 5: 提交任务 4**

```bash
cd /Users/xsw/custom_idea_project/qqwer
git add log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/stats/service/AiQueryService.java \
  log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/LlmSqlCandidateProvider.java \
  log-analysis-backend/log-analysis-app/src/test/java/cn/mw/loganalysis/agent/service/LlmSqlCandidateProviderTest.java
git commit -m "feat: add llm sql-only candidate provider"
```

## Task 5: 候选竞争服务和线程池

**Files:**

- Create/Modify: `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/SqlCandidateResult.java`
- Create: `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/config/AgentText2SqlExecutorConfig.java`
- Create: `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/SqlCandidateRaceService.java`
- Test: `log-analysis-backend/log-analysis-app/src/test/java/cn/mw/loganalysis/agent/service/SqlCandidateRaceServiceTest.java`

- [ ] **Step 1: 写竞争服务失败测试**

Create `log-analysis-backend/log-analysis-app/src/test/java/cn/mw/loganalysis/agent/service/SqlCandidateRaceServiceTest.java`:

```java
package cn.mw.loganalysis.agent.service;

import cn.mw.loganalysis.agent.repository.AgentSqlQueryExampleRepository;
import cn.mw.loganalysis.stats.service.DynamicLogQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SqlCandidateRaceServiceTest {

    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final SqlCandidateValidator validator = mock(SqlCandidateValidator.class);
    private final DynamicLogQueryService dynamicLogQueryService = mock(DynamicLogQueryService.class);
    private final AgentSqlQueryExampleRepository repository = mock(AgentSqlQueryExampleRepository.class);
    private final SqlQuestionNormalizer normalizer = new SqlQuestionNormalizer();
    private final AgentExecutionContext context = new AgentExecutionContext(
            "sink-1",
            "syslog_logs_sink",
            "clickhouse",
            1L,
            "session-1"
    );

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void shouldUseTemplateBeforeStartingLlm() {
        AtomicBoolean llmCalled = new AtomicBoolean(false);
        SqlCandidate templateCandidate = candidate("template", "SELECT count() AS total FROM `syslog_logs` LIMIT 200", "metric");
        SqlCandidateProvider template = provider("template", 10, Optional.of(templateCandidate));
        SqlCandidateProvider llm = provider("llm", 100, () -> {
            llmCalled.set(true);
            return Optional.of(candidate("llm", "SELECT * FROM `syslog_logs` LIMIT 10", "list"));
        });
        SqlCandidateRaceService service = service(List.of(template, llm));
        when(validator.validate(context, templateCandidate)).thenReturn(SqlCandidateValidationResult.valid(templateCandidate.sql()));
        when(dynamicLogQueryService.executeRawSQL("sink-1", templateCandidate.sql())).thenReturn(List.of(Map.of("total", 42L)));

        SqlCandidateResult result = service.query(context, "查最近一小时的日志数据总数");

        assertThat(result.candidateSource()).isEqualTo("template");
        assertThat(result.response().getResult()).isEqualTo(List.of(Map.of("total", 42L)));
        assertThat(llmCalled).isFalse();
        verify(repository).saveSuccess(eq(1L), eq("sink-1"), eq("clickhouse"), eq("查最近一小时的日志数据总数"), any(), eq(templateCandidate.sql()), eq("metric"));
    }

    @Test
    void shouldTryLlmWhenCheapCandidatesDoNotMatch() {
        SqlCandidate llmCandidate = candidate("llm", "SELECT `severity` FROM `syslog_logs` LIMIT 10", "list");
        SqlCandidateProvider template = provider("template", 10, Optional.empty());
        SqlCandidateProvider history = provider("history", 20, Optional.empty());
        SqlCandidateProvider llm = provider("llm", 100, Optional.of(llmCandidate));
        SqlCandidateRaceService service = service(List.of(template, history, llm));
        when(validator.validate(context, llmCandidate)).thenReturn(SqlCandidateValidationResult.valid(llmCandidate.sql()));
        when(dynamicLogQueryService.executeRawSQL("sink-1", llmCandidate.sql())).thenReturn(List.of(Map.of("severity", "ERROR")));

        SqlCandidateResult result = service.query(context, "查询最近日志级别");

        assertThat(result.candidateSource()).isEqualTo("llm");
        assertThat(result.validatedCandidates()).contains("llm");
    }

    @Test
    void shouldRejectInvalidCandidateAndUseNextCandidate() {
        SqlCandidate invalid = candidate("template", "DROP TABLE `syslog_logs`", "metric");
        SqlCandidate valid = candidate("llm", "SELECT * FROM `syslog_logs` LIMIT 10", "list");
        SqlCandidateRaceService service = service(List.of(
                provider("template", 10, Optional.of(invalid)),
                provider("llm", 100, Optional.of(valid))
        ));
        when(validator.validate(context, invalid)).thenReturn(SqlCandidateValidationResult.invalid("SQL 包含禁止关键字"));
        when(validator.validate(context, valid)).thenReturn(SqlCandidateValidationResult.valid(valid.sql()));
        when(dynamicLogQueryService.executeRawSQL("sink-1", valid.sql())).thenReturn(List.of(Map.of("message", "ok")));

        SqlCandidateResult result = service.query(context, "复杂查询");

        assertThat(result.candidateSource()).isEqualTo("llm");
        assertThat(result.rejectedCandidates()).contains("template: SQL 包含禁止关键字");
    }

    @Test
    void shouldMarkHistoryCandidateUsed() {
        SqlCandidate history = SqlCandidate.builder()
                .source("history")
                .sql("SELECT * FROM `syslog_logs` LIMIT 10")
                .resultType("list")
                .confidence(0.8D)
                .metadata(Map.of("exampleId", 99L))
                .build();
        SqlCandidateRaceService service = service(List.of(provider("history", 20, Optional.of(history))));
        when(validator.validate(context, history)).thenReturn(SqlCandidateValidationResult.valid(history.sql()));
        when(dynamicLogQueryService.executeRawSQL("sink-1", history.sql())).thenReturn(List.of(Map.of("message", "ok")));

        SqlCandidateResult result = service.query(context, "查最近日志");

        assertThat(result.candidateSource()).isEqualTo("history");
        verify(repository).markUsed(99L);
        verify(repository, never()).saveSuccess(eq(1L), eq("sink-1"), eq("clickhouse"), any(), any(), any(), any());
    }

    private SqlCandidateRaceService service(List<SqlCandidateProvider> providers) {
        return new SqlCandidateRaceService(
                providers,
                validator,
                dynamicLogQueryService,
                repository,
                normalizer,
                executor
        );
    }

    private SqlCandidate candidate(String source, String sql, String resultType) {
        return SqlCandidate.builder()
                .source(source)
                .sql(sql)
                .resultType(resultType)
                .confidence(1D)
                .generationTimeMs(1L)
                .metadata(Map.of())
                .build();
    }

    private SqlCandidateProvider provider(String source, int order, Optional<SqlCandidate> candidate) {
        return provider(source, order, () -> candidate);
    }

    private SqlCandidateProvider provider(String source, int order, CandidateSupplier supplier) {
        return new SqlCandidateProvider() {
            @Override
            public String source() {
                return source;
            }

            @Override
            public boolean supports(AgentExecutionContext context, String query) {
                return true;
            }

            @Override
            public Optional<SqlCandidate> generate(AgentExecutionContext context, String query) {
                return supplier.get();
            }

            @Override
            public int getOrder() {
                return order;
            }
        };
    }

    private interface CandidateSupplier {
        Optional<SqlCandidate> get();
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```bash
cd /Users/xsw/custom_idea_project/qqwer/log-analysis-backend
mvn -pl log-analysis-app -Dtest=SqlCandidateRaceServiceTest test
```

Expected: FAIL，因为 `SqlCandidateRaceService` 或 `SqlCandidateResult` 尚未实现。

- [ ] **Step 3: 实现竞争服务和线程池**

Ensure `SqlCandidateResult.java`:

```java
package cn.mw.loganalysis.agent.service;

import cn.mw.loganalysis.stats.dto.AiQueryResponse;
import lombok.Builder;

import java.util.List;

/**
 * 候选竞争后的最终结果。
 */
@Builder
record SqlCandidateResult(AiQueryResponse response,
                          String candidateSource,
                          long raceMs,
                          List<String> validatedCandidates,
                          List<String> rejectedCandidates) {
}
```

Create `AgentText2SqlExecutorConfig.java`:

```java
package cn.mw.loganalysis.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 智能助手 Text2SQL 候选竞争线程池配置。
 */
@Configuration
public class AgentText2SqlExecutorConfig {

    /**
     * Text2SQL 候选生成和校验执行使用的轻量线程池。
     */
    @Bean("agentText2SqlExecutor")
    public Executor agentText2SqlExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("agent-text2sql-");
        executor.setKeepAliveSeconds(60);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
```

Create `SqlCandidateRaceService.java`:

```java
package cn.mw.loganalysis.agent.service;

import cn.mw.loganalysis.agent.repository.AgentSqlQueryExampleRepository;
import cn.mw.loganalysis.stats.dto.AiQueryResponse;
import cn.mw.loganalysis.stats.service.DynamicLogQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionService;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Text2SQL 候选竞争服务。
 */
@Slf4j
@Service
public class SqlCandidateRaceService {

    private static final long LLM_DELAY_MS = 150L;
    private static final long RACE_TIMEOUT_MS = 10_000L;

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
        this.providers = new ArrayList<>(providers);
        AnnotationAwareOrderComparator.sort(this.providers);
        this.validator = validator;
        this.dynamicLogQueryService = dynamicLogQueryService;
        this.exampleRepository = exampleRepository;
        this.questionNormalizer = questionNormalizer;
        this.executor = executor;
    }

    /**
     * 执行候选竞争，返回首个校验和执行都成功的 SQL 查询结果。
     */
    public SqlCandidateResult query(AgentExecutionContext context, String query) {
        long startedAt = System.currentTimeMillis();
        List<String> validatedCandidates = new ArrayList<>();
        List<String> rejectedCandidates = new ArrayList<>();
        List<Future<CandidateAttempt>> futures = new ArrayList<>();
        CompletionService<CandidateAttempt> completionService = new ExecutorCompletionService<>(executor);

        List<SqlCandidateProvider> cheapProviders = providers.stream()
                .filter(provider -> !"llm".equals(provider.source()))
                .toList();
        List<SqlCandidateProvider> llmProviders = providers.stream()
                .filter(provider -> "llm".equals(provider.source()))
                .toList();

        submitProviders(completionService, futures, cheapProviders, context, query);

        CandidateAttempt winner = waitForWinner(context, query, startedAt, completionService, futures,
                validatedCandidates, rejectedCandidates, cheapProviders.size(), LLM_DELAY_MS);
        if (winner != null) {
            cancelPending(futures);
            return buildResult(winner, startedAt, validatedCandidates, rejectedCandidates);
        }

        submitProviders(completionService, futures, llmProviders, context, query);
        int totalSubmitted = futures.size();
        winner = waitForWinner(context, query, startedAt, completionService, futures,
                validatedCandidates, rejectedCandidates, totalSubmitted, RACE_TIMEOUT_MS);
        if (winner != null) {
            cancelPending(futures);
            return buildResult(winner, startedAt, validatedCandidates, rejectedCandidates);
        }

        cancelPending(futures);
        throw new IllegalStateException("没有可执行的安全 SQL 候选：" + String.join("；", rejectedCandidates));
    }

    /**
     * 提交支持当前查询的候选 Provider。
     */
    private void submitProviders(CompletionService<CandidateAttempt> completionService,
                                 List<Future<CandidateAttempt>> futures,
                                 List<SqlCandidateProvider> candidateProviders,
                                 AgentExecutionContext context,
                                 String query) {
        for (SqlCandidateProvider provider : candidateProviders) {
            if (!provider.supports(context, query)) {
                continue;
            }
            futures.add(completionService.submit(() -> evaluate(provider, context, query)));
        }
    }

    /**
     * 等待候选完成，并返回第一个成功候选。
     */
    private CandidateAttempt waitForWinner(AgentExecutionContext context,
                                           String query,
                                           long startedAt,
                                           CompletionService<CandidateAttempt> completionService,
                                           List<Future<CandidateAttempt>> futures,
                                           List<String> validatedCandidates,
                                           List<String> rejectedCandidates,
                                           int expectedCompletions,
                                           long waitMs) {
        int completed = 0;
        long deadline = System.currentTimeMillis() + waitMs;
        while (completed < expectedCompletions && System.currentTimeMillis() < deadline) {
            long remaining = Math.max(1L, deadline - System.currentTimeMillis());
            try {
                Future<CandidateAttempt> future = completionService.poll(remaining, TimeUnit.MILLISECONDS);
                if (future == null) {
                    break;
                }
                completed++;
                CandidateAttempt attempt = future.get();
                if (attempt.success()) {
                    validatedCandidates.add(attempt.source());
                    recordSuccess(context, query, attempt);
                    return attempt;
                }
                rejectedCandidates.add(attempt.source() + ": " + attempt.reason());
            } catch (Exception ex) {
                rejectedCandidates.add("race: " + describe(ex));
            }
        }
        return null;
    }

    /**
     * 生成、校验并执行单个候选。
     */
    private CandidateAttempt evaluate(SqlCandidateProvider provider, AgentExecutionContext context, String query) {
        try {
            Optional<SqlCandidate> optional = provider.generate(context, query);
            if (optional.isEmpty()) {
                return CandidateAttempt.rejected(provider.source(), "no candidate");
            }
            SqlCandidate candidate = optional.get();
            SqlCandidateValidationResult validation = validator.validate(context, candidate);
            if (!validation.valid()) {
                return CandidateAttempt.rejected(candidate.source(), validation.reason());
            }
            long sqlStartedAt = System.currentTimeMillis();
            Object result = dynamicLogQueryService.executeRawSQL(context.datasourceId(), validation.sql());
            double sqlExecutionTime = (System.currentTimeMillis() - sqlStartedAt) / 1000.0;
            AiQueryResponse response = AiQueryResponse.builder()
                    .success(true)
                    .sql(validation.sql())
                    .result(result)
                    .sqlGenerationTime(candidate.generationTimeMs() / 1000.0)
                    .sqlExecutionTime(sqlExecutionTime)
                    .build();
            return CandidateAttempt.success(candidate, response);
        } catch (Exception ex) {
            log.warn("Text2SQL 候选执行失败, source={}", provider.source(), ex);
            return CandidateAttempt.rejected(provider.source(), describe(ex));
        }
    }

    /**
     * 记录成功候选的历史经验。
     */
    private void recordSuccess(AgentExecutionContext context, String query, CandidateAttempt attempt) {
        if ("history".equals(attempt.source())) {
            Object exampleId = attempt.candidate().metadata() != null ? attempt.candidate().metadata().get("exampleId") : null;
            if (exampleId instanceof Number number) {
                exampleRepository.markUsed(number.longValue());
            }
            return;
        }
        exampleRepository.saveSuccess(
                context.userId(),
                context.datasourceId(),
                context.datasourceType(),
                query,
                questionNormalizer.normalize(query),
                attempt.response().getSql(),
                attempt.candidate().resultType()
        );
    }

    private SqlCandidateResult buildResult(CandidateAttempt winner,
                                           long startedAt,
                                           List<String> validatedCandidates,
                                           List<String> rejectedCandidates) {
        double totalTime = (System.currentTimeMillis() - startedAt) / 1000.0;
        winner.response().setTotalExecutionTime(totalTime);
        return SqlCandidateResult.builder()
                .response(winner.response())
                .candidateSource(winner.source())
                .raceMs(System.currentTimeMillis() - startedAt)
                .validatedCandidates(List.copyOf(validatedCandidates))
                .rejectedCandidates(List.copyOf(rejectedCandidates))
                .build();
    }

    private void cancelPending(List<Future<CandidateAttempt>> futures) {
        for (Future<CandidateAttempt> future : futures) {
            if (!future.isDone()) {
                future.cancel(true);
            }
        }
    }

    private String describe(Exception ex) {
        Throwable current = ex;
        while (current != null) {
            String message = StringUtils.trimToNull(current.getMessage());
            if (message != null) {
                return message;
            }
            current = current.getCause();
        }
        return ex.getClass().getSimpleName();
    }

    private record CandidateAttempt(String source,
                                    SqlCandidate candidate,
                                    AiQueryResponse response,
                                    boolean success,
                                    String reason) {

        static CandidateAttempt success(SqlCandidate candidate, AiQueryResponse response) {
            return new CandidateAttempt(candidate.source(), candidate, response, true, null);
        }

        static CandidateAttempt rejected(String source, String reason) {
            return new CandidateAttempt(source, null, null, false, reason);
        }
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run:

```bash
cd /Users/xsw/custom_idea_project/qqwer/log-analysis-backend
mvn -pl log-analysis-app -Dtest=SqlCandidateRaceServiceTest test
```

Expected: PASS.

- [ ] **Step 5: 提交任务 5**

```bash
cd /Users/xsw/custom_idea_project/qqwer
git add log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/SqlCandidateResult.java \
  log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/config/AgentText2SqlExecutorConfig.java \
  log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/SqlCandidateRaceService.java \
  log-analysis-backend/log-analysis-app/src/test/java/cn/mw/loganalysis/agent/service/SqlCandidateRaceServiceTest.java
git commit -m "feat: race text2sql sql candidates"
```

## Task 6: 接入 Text2SqlToolHandler 并移除旧 preflight

**Files:**

- Modify: `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/Text2SqlToolHandler.java`
- Delete: `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/Text2SqlPreflightStrategy.java`
- Delete: `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/LogCountMetricPreflightStrategy.java`
- Delete: `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/DimensionCountPreflightStrategy.java`
- Test: `log-analysis-backend/log-analysis-app/src/test/java/cn/mw/loganalysis/agent/service/Text2SqlToolHandlerTest.java`

- [ ] **Step 1: 写 Handler 接入失败测试**

Create `log-analysis-backend/log-analysis-app/src/test/java/cn/mw/loganalysis/agent/service/Text2SqlToolHandlerTest.java`:

```java
package cn.mw.loganalysis.agent.service;

import cn.mw.loganalysis.stats.dto.AiQueryResponse;
import cn.mw.loganalysis.stats.service.DynamicLogQueryService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Text2SqlToolHandlerTest {

    private final SqlCandidateRaceService raceService = mock(SqlCandidateRaceService.class);
    private final DynamicLogQueryService dynamicLogQueryService = mock(DynamicLogQueryService.class);
    private final Text2SqlToolHandler handler = new Text2SqlToolHandler(raceService, dynamicLogQueryService);
    private final AgentExecutionContext context = new AgentExecutionContext(
            "sink-1",
            "syslog_logs_sink",
            "clickhouse",
            1L,
            "session-1"
    );

    @Test
    void shouldReturnText2SqlCardWithCandidateSummary() {
        AiQueryResponse response = AiQueryResponse.builder()
                .success(true)
                .sql("SELECT count() AS total FROM `syslog_logs` LIMIT 200")
                .result(List.of(Map.of("total", 42L)))
                .sqlGenerationTime(0.001D)
                .sqlExecutionTime(0.02D)
                .totalExecutionTime(0.03D)
                .build();
        when(raceService.query(context, "查最近一小时的日志数据总数")).thenReturn(SqlCandidateResult.builder()
                .response(response)
                .candidateSource("template")
                .raceMs(12L)
                .validatedCandidates(List.of("template"))
                .rejectedCandidates(List.of())
                .build());
        when(dynamicLogQueryService.getTableName("sink-1")).thenReturn("syslog_logs");

        AgentToolPayload payload = handler.handle(context, "查最近一小时的日志数据总数");

        assertThat(payload.getToolName()).isEqualTo("text2sql_query");
        assertThat(payload.getResult().getType()).isEqualTo("text2sql");
        assertThat(payload.getResult().getQueryResultType()).isEqualTo("metric");
        assertThat(payload.getResult().getRows()).containsExactly(Map.of("total", 42L));
        assertThat(payload.getResult().getSummary()).containsEntry("candidateSource", "template");
        assertThat(payload.getResult().getSummary()).containsEntry("candidateRaceMs", 12L);
        verify(raceService).query(context, "查最近一小时的日志数据总数");
    }

    @Test
    void shouldRejectNonClickHouseDatasource() {
        AgentExecutionContext postgres = new AgentExecutionContext("pg-1", "pg", "postgresql", 1L, "session-1");

        assertThatThrownBy(() -> handler.handle(postgres, "查最近一小时日志"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("仅支持 ClickHouse");
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run:

```bash
cd /Users/xsw/custom_idea_project/qqwer/log-analysis-backend
mvn -pl log-analysis-app -Dtest=Text2SqlToolHandlerTest test
```

Expected: FAIL，因为 handler 仍依赖 `AiQueryService` 和旧 preflight 构造器。

- [ ] **Step 3: 改造 Handler 并删除旧 preflight 类**

Update `Text2SqlToolHandler.java` constructor and `handle` logic:

```java
private final SqlCandidateRaceService raceService;
private final DynamicLogQueryService dynamicLogQueryService;

/**
 * 注入候选竞争服务和动态查询服务。
 */
public Text2SqlToolHandler(SqlCandidateRaceService raceService,
                           DynamicLogQueryService dynamicLogQueryService) {
    this.raceService = raceService;
    this.dynamicLogQueryService = dynamicLogQueryService;
}
```

Replace old AI call block with:

```java
SqlCandidateResult candidateResult = raceService.query(context, StringUtils.defaultString(StringUtils.trimToNull(query)));
AiQueryResponse response = candidateResult.response();
if (!Boolean.TRUE.equals(response.getSuccess())) {
    throw new IllegalStateException(StringUtils.isNotBlank(response.getError()) ? response.getError() : "text2sql 查询失败");
}
```

Add summary fields:

```java
summary.put("candidateSource", candidateResult.candidateSource());
summary.put("candidateRaceMs", candidateResult.raceMs());
summary.put("validatedCandidates", candidateResult.validatedCandidates());
summary.put("rejectedCandidates", candidateResult.rejectedCandidates());
```

Delete:

```bash
rm log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/Text2SqlPreflightStrategy.java
rm log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/LogCountMetricPreflightStrategy.java
rm log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/DimensionCountPreflightStrategy.java
```

- [ ] **Step 4: 运行测试确认通过**

Run:

```bash
cd /Users/xsw/custom_idea_project/qqwer/log-analysis-backend
mvn -pl log-analysis-app -Dtest=Text2SqlToolHandlerTest test
```

Expected: PASS.

- [ ] **Step 5: 提交任务 6**

```bash
cd /Users/xsw/custom_idea_project/qqwer
git add log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/Text2SqlToolHandler.java \
  log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/Text2SqlPreflightStrategy.java \
  log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/LogCountMetricPreflightStrategy.java \
  log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/DimensionCountPreflightStrategy.java \
  log-analysis-backend/log-analysis-app/src/test/java/cn/mw/loganalysis/agent/service/Text2SqlToolHandlerTest.java
git commit -m "feat: route text2sql through candidate race"
```

## Task 7: 回归测试和编译验证

**Files:**

- Modify only if tests reveal compile/runtime issues:
  - `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/SqlCandidateRaceService.java`
  - `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/Text2SqlToolHandler.java`
  - `log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/stats/service/AiQueryService.java`

- [ ] **Step 1: 运行全部 Text2SQL 候选测试**

Run:

```bash
cd /Users/xsw/custom_idea_project/qqwer/log-analysis-backend
mvn -pl log-analysis-app -Dtest=TemplateSqlCandidateProviderTest,HistorySqlCandidateProviderTest,SqlCandidateValidatorTest,LlmSqlCandidateProviderTest,SqlCandidateRaceServiceTest,Text2SqlToolHandlerTest test
```

Expected: PASS.

- [ ] **Step 2: 运行后端主模块编译**

Run:

```bash
cd /Users/xsw/custom_idea_project/qqwer/log-analysis-backend
mvn -pl log-analysis-app -DskipTests compile
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: 搜索旧 preflight 引用**

Run:

```bash
cd /Users/xsw/custom_idea_project/qqwer
rg "Text2SqlPreflightStrategy|LogCountMetricPreflightStrategy|DimensionCountPreflightStrategy" log-analysis-backend/log-analysis-app/src/main/java log-analysis-backend/log-analysis-app/src/test/java
```

Expected: no matches.

- [ ] **Step 4: 检查 Liquibase 文件被 master 引用**

Run:

```bash
cd /Users/xsw/custom_idea_project/qqwer
rg "20260528-agent-sql-query-examples.sql" log-analysis-backend/log-analysis-app/src/main/resources/db/changelog/db.changelog-master.yaml
```

Expected: one match.

- [ ] **Step 5: 提交验证修复**

If Step 1-4 required fixes:

```bash
cd /Users/xsw/custom_idea_project/qqwer
git add log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/SqlCandidateRaceService.java \
  log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/agent/service/Text2SqlToolHandler.java \
  log-analysis-backend/log-analysis-app/src/main/java/cn/mw/loganalysis/stats/service/AiQueryService.java
git commit -m "fix: stabilize text2sql candidate race"
```

If no fixes were needed, do not create an empty commit.

## 自检结果

- Spec 覆盖：模板候选、历史候选、LLM 候选、统一校验、候选竞争、历史落库、handler 结果协议、旧 preflight 清理、验证命令均已对应到任务。
- 占位符检查：计划没有使用待补充占位内容；每个实现任务都有目标文件、测试代码、运行命令和提交命令。
- 类型一致性：计划统一使用 `SqlCandidate`、`SqlCandidateProvider`、`SqlCandidateValidationResult`、`SqlCandidateResult`、`SqlCandidateRaceService`、`AgentSqlQueryExampleRepository` 命名。
