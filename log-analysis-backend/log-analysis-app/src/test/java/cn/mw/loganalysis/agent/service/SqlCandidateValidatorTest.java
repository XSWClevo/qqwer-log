package cn.mw.loganalysis.agent.service;

import cn.mw.loganalysis.stats.service.DynamicLogQueryService;
import cn.mw.loganalysis.stats.service.query.FieldInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

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
            1001L,
            "session-1"
    );

    @Test
    void shouldAcceptSafeSelectAndAppendDefaultLimit() {
        givenCurrentTableSchema();

        SqlCandidateValidationResult result = validator.validate(
                context,
                candidate("SELECT `timestamp`, `message` FROM `syslog_logs` WHERE `severity` = 'ERROR';")
        );

        assertThat(result.valid()).isTrue();
        assertThat(result.sql()).isEqualTo(
                "SELECT `timestamp`, `message` FROM `syslog_logs` WHERE `severity` = 'ERROR' LIMIT 200"
        );
        assertThat(result.reason()).isNull();
    }

    @Test
    void shouldRejectDangerousSql() {
        givenCurrentTableSchema();

        assertThat(validator.validate(context, candidate("DELETE FROM `syslog_logs`")).valid()).isFalse();
        assertThat(validator.validate(context, candidate("DROP TABLE `syslog_logs`")).valid()).isFalse();
        assertThat(validator.validate(context, candidate("SHOW TABLES")).valid()).isFalse();
    }

    @Test
    void shouldRejectCrossTableSql() {
        givenCurrentTableSchema();

        SqlCandidateValidationResult result = validator.validate(
                context,
                candidate("SELECT `message` FROM `syslog_logs` JOIN `audit_logs` ON `syslog_logs`.`id` = `audit_logs`.`id`")
        );

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).contains("表");
    }

    @Test
    void shouldRejectCommaSeparatedCrossTableSql() {
        givenCurrentTableSchema();

        SqlCandidateValidationResult result = validator.validate(
                context,
                candidate("SELECT message FROM syslog_logs, audit_logs")
        );

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).contains("表");
    }

    @Test
    void shouldRejectDerivedTableSourceSql() {
        givenCurrentTableSchema();

        SqlCandidateValidationResult result = validator.validate(
                context,
                candidate("SELECT s.message FROM `syslog_logs` s JOIN (SELECT * FROM audit_logs) a ON s.id = a.id")
        );

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).contains("子查询").contains("派生表");
    }

    @Test
    void shouldRejectUnknownBacktickedFields() {
        givenCurrentTableSchema();

        SqlCandidateValidationResult result = validator.validate(
                context,
                candidate("SELECT `message`, `unknown_field` FROM `syslog_logs`")
        );

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).contains("字段");
    }

    @Test
    void shouldKeepExistingLimit() {
        givenCurrentTableSchema();

        SqlCandidateValidationResult result = validator.validate(
                context,
                candidate("SELECT `message` FROM `syslog_logs` ORDER BY `timestamp` DESC LIMIT 20")
        );

        assertThat(result.valid()).isTrue();
        assertThat(result.sql()).isEqualTo("SELECT `message` FROM `syslog_logs` ORDER BY `timestamp` DESC LIMIT 20");
    }

    @Test
    void shouldRejectLimitBySql() {
        givenCurrentTableSchema();

        SqlCandidateValidationResult result = validator.validate(
                context,
                candidate("SELECT `message`, `severity` FROM `syslog_logs` LIMIT 10 BY `severity`")
        );

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).contains("LIMIT BY");
    }

    @Test
    void shouldNormalizeTooLargeLimit() {
        givenCurrentTableSchema();

        SqlCandidateValidationResult result = validator.validate(
                context,
                candidate("SELECT `message` FROM `syslog_logs` LIMIT 1000000")
        );

        assertThat(result.valid()).isTrue();
        assertThat(result.sql()).isEqualTo("SELECT `message` FROM `syslog_logs` LIMIT 200");
    }

    @Test
    void shouldNormalizeOverflowLimitNumber() {
        givenCurrentTableSchema();

        SqlCandidateValidationResult result = validator.validate(
                context,
                candidate("SELECT `message` FROM `syslog_logs` LIMIT 999999999999999999999999")
        );

        assertThat(result.valid()).isTrue();
        assertThat(result.sql()).isEqualTo("SELECT `message` FROM `syslog_logs` LIMIT 200");
    }

    @Test
    void shouldNormalizeTooLargeOffsetLimitCount() {
        givenCurrentTableSchema();

        SqlCandidateValidationResult result = validator.validate(
                context,
                candidate("SELECT `message` FROM `syslog_logs` LIMIT 0, 1000000")
        );

        assertThat(result.valid()).isTrue();
        assertThat(result.sql()).isEqualTo("SELECT `message` FROM `syslog_logs` LIMIT 200");
    }

    @Test
    void shouldAppendLimitWhenLimitIsOnlyAlias() {
        givenCurrentTableSchema();

        SqlCandidateValidationResult result = validator.validate(
                context,
                candidate("SELECT count(*) AS limit FROM syslog_logs")
        );

        assertThat(result.valid()).isTrue();
        assertThat(result.sql()).isEqualTo("SELECT count(*) AS limit FROM syslog_logs LIMIT 200");
    }

    @Test
    void shouldAllowBacktickedSelectAlias() {
        givenCurrentTableSchema();

        SqlCandidateValidationResult result = validator.validate(
                context,
                candidate("SELECT count() AS `total` FROM `syslog_logs`")
        );

        assertThat(result.valid()).isTrue();
        assertThat(result.sql()).isEqualTo("SELECT count() AS `total` FROM `syslog_logs` LIMIT 200");
    }

    @Test
    void shouldAllowBacktickedSelectAliasWithoutAs() {
        givenCurrentTableSchema();

        SqlCandidateValidationResult result = validator.validate(
                context,
                candidate("SELECT count() `total` FROM `syslog_logs`")
        );

        assertThat(result.valid()).isTrue();
        assertThat(result.sql()).isEqualTo("SELECT count() `total` FROM `syslog_logs` LIMIT 200");
    }

    @Test
    void shouldIgnoreKeywordsInsideStringLiterals() {
        givenCurrentTableSchema();

        SqlCandidateValidationResult result = validator.validate(
                context,
                candidate("SELECT `message` FROM `syslog_logs` WHERE `message` = 'delete limit drop'")
        );

        assertThat(result.valid()).isTrue();
        assertThat(result.sql()).endsWith("LIMIT 200");
    }

    @Test
    void shouldAllowSemicolonInsideStringLiteral() {
        givenCurrentTableSchema();

        SqlCandidateValidationResult result = validator.validate(
                context,
                candidate("SELECT `message` FROM `syslog_logs` WHERE `message` = 'a;b'")
        );

        assertThat(result.valid()).isTrue();
        assertThat(result.sql()).isEqualTo("SELECT `message` FROM `syslog_logs` WHERE `message` = 'a;b' LIMIT 200");
    }

    @Test
    void shouldAllowLineCommentMarkerInsideStringLiteral() {
        givenCurrentTableSchema();

        SqlCandidateValidationResult result = validator.validate(
                context,
                candidate("SELECT `message` FROM `syslog_logs` WHERE `message` = '-- comment-like text'")
        );

        assertThat(result.valid()).isTrue();
        assertThat(result.sql()).endsWith("LIMIT 200");
    }

    @Test
    void shouldAllowBlockCommentMarkerInsideStringLiteral() {
        givenCurrentTableSchema();

        SqlCandidateValidationResult result = validator.validate(
                context,
                candidate("SELECT `message` FROM `syslog_logs` WHERE `message` = '/* comment-like text */'")
        );

        assertThat(result.valid()).isTrue();
        assertThat(result.sql()).endsWith("LIMIT 200");
    }

    @Test
    void shouldAllowEscapedQuoteBeforeCommentMarkerInsideStringLiteral() {
        givenCurrentTableSchema();

        SqlCandidateValidationResult result = validator.validate(
                context,
                candidate("SELECT `message` FROM `syslog_logs` WHERE `message` = 'can\\'t -- comment-like text'")
        );

        assertThat(result.valid()).isTrue();
        assertThat(result.sql()).endsWith("LIMIT 200");
    }

    @Test
    void shouldRejectInternalSemicolonMultiStatementSql() {
        givenCurrentTableSchema();

        SqlCandidateValidationResult result = validator.validate(
                context,
                candidate("SELECT `message` FROM `syslog_logs`; SELECT `message` FROM `syslog_logs`")
        );

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).contains("单条");
    }

    @Test
    void shouldRejectSqlWithLineComment() {
        givenCurrentTableSchema();

        SqlCandidateValidationResult result = validator.validate(
                context,
                candidate("SELECT message FROM syslog_logs -- LIMIT 1")
        );

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).contains("注释");
    }

    @Test
    void shouldRejectSqlWithBlockComment() {
        givenCurrentTableSchema();

        SqlCandidateValidationResult result = validator.validate(
                context,
                candidate("SELECT message FROM syslog_logs /* LIMIT 1 */")
        );

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).contains("注释");
    }

    private void givenCurrentTableSchema() {
        when(dynamicLogQueryService.getTableName("sink-1")).thenReturn("syslog_logs");
        when(dynamicLogQueryService.getTableSchema("sink-1")).thenReturn(List.of(
                field("id"),
                field("timestamp"),
                field("severity"),
                field("message")
        ));
    }

    private FieldInfo field(String name) {
        return FieldInfo.builder()
                .name(name)
                .type("String")
                .build();
    }

    private SqlCandidate candidate(String sql) {
        return SqlCandidate.builder()
                .source("llm")
                .sql(sql)
                .resultType("table")
                .confidence(0.8D)
                .build();
    }
}
