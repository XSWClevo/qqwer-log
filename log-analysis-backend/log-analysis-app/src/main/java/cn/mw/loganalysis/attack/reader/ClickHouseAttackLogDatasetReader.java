package cn.mw.loganalysis.attack.reader;

import cn.mw.loganalysis.attack.entity.AttackLogDataset;
import cn.mw.loganalysis.attack.mapper.AttackClassificationMapper;
import cn.mw.loganalysis.attack.mapper.param.AttackDatasetScanSqlParam;
import cn.mw.loganalysis.attack.model.NormalizedLogRecord;
import cn.mw.loganalysis.common.util.DateTimeUtils;
import cn.mw.loganalysis.stats.service.query.ClickHouseOperationStrategy;
import cn.mw.loganalysis.stats.service.query.DatasourceConnectionConfig;
import cn.mw.loganalysis.stats.service.query.support.DynamicMyBatisUtils;
import cn.mw.loganalysis.stats.service.query.support.StatsQueryMapperUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClickHouseAttackLogDatasetReader implements AttackLogDatasetReader {

    private static final DateTimeFormatter[] SUPPORTED_TIME_FORMATTERS = new DateTimeFormatter[]{
            DateTimeFormatter.ofPattern(DateTimeUtils.PATTERN_DATETIME_MILLIS),
            DateTimeFormatter.ofPattern(DateTimeUtils.PATTERN_DATETIME)
    };

    private final AttackDatasourceConfigResolver datasourceConfigResolver;
    private final ClickHouseOperationStrategy clickHouseOperationStrategy;

    @Override
    public boolean supports(String datasourceType) {
        return StringUtils.equalsIgnoreCase(datasourceType, "clickhouse");
    }

    @Override
    public List<NormalizedLogRecord> read(AttackLogDataset dataset, LocalDateTime startTime, LocalDateTime endTime, int limit) {
        Map<String, String> mapping = dataset.getFieldMapping();
        if (MapUtils.isEmpty(mapping)) {
            log.warn("攻击分类数据集字段映射为空: id={}, name={}", dataset.getId(), dataset.getName());
            return Collections.emptyList();
        }

        String timeField = requiredMapping(mapping, "timestamp");
        String messageField = requiredMapping(mapping, "message");
        DatasourceConnectionConfig datasourceConfig = datasourceConfigResolver.resolve(dataset);
        String tableExpression = StatsQueryMapperUtils.qualifyClickHouseTable(
                datasourceConfig.getDatabase(),
                StringUtils.defaultIfBlank(datasourceConfig.getTable(), dataset.getTableName()));

        AttackDatasetScanSqlParam param = AttackDatasetScanSqlParam.builder()
                .tableExpression(tableExpression)
                .timeExpression(quoteField(timeField))
                .messageExpression(quoteField(messageField))
                .rawExpression(optionalField(mapping, "raw"))
                .sourceIpExpression(optionalField(mapping, "sourceIp"))
                .hostnameExpression(optionalField(mapping, "hostname"))
                .severityExpression(optionalField(mapping, "severity"))
                .startTime(DateTimeUtils.formatWithMillis(startTime))
                .endTime(DateTimeUtils.formatWithMillis(endTime))
                .limit(limit)
                .build();

        return DynamicMyBatisUtils.execute(
                        clickHouseOperationStrategy.getSqlSessionFactory(datasourceConfig, AttackClassificationMapper.class),
                        AttackClassificationMapper.class,
                        mapper -> mapper.selectDatasetLogs(param))
                .stream()
                .map(row -> toRecord(dataset, row))
                .toList();
    }

    private String requiredMapping(Map<String, String> mapping, String standardField) {
        String field = mapping.get(standardField);
        if (StringUtils.isBlank(field)) {
            throw new IllegalArgumentException("数据集缺少必需字段映射: " + standardField);
        }
        return StringUtils.trim(field);
    }

    private String optionalField(Map<String, String> mapping, String standardField) {
        String field = mapping.get(standardField);
        if (StringUtils.isBlank(field)) {
            return "''";
        }
        return quoteField(field);
    }

    private String quoteField(String field) {
        return StatsQueryMapperUtils.quoteClickHouseIdentifier(StringUtils.trim(field));
    }

    private NormalizedLogRecord toRecord(AttackLogDataset dataset, Map<String, Object> row) {
        LocalDateTime timestamp = toLocalDateTime(row.get("log_timestamp"));
        String sourceIp = toString(row.get("source_ip"));
        String hostname = toString(row.get("hostname"));
        String message = toString(row.get("message"));
        String raw = toString(row.get("raw"));
        String severity = toString(row.get("severity"));

        return NormalizedLogRecord.builder()
                .timestamp(timestamp)
                .sourceIp(sourceIp)
                .hostname(hostname)
                .message(message)
                .raw(raw)
                .severity(severity)
                .fingerprint(FingerprintUtils.logFingerprint(dataset, timestamp, sourceIp, hostname, message, raw))
                .build();
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (ObjectUtils.isEmpty(value)) {
            return null;
        }

        String text = StringUtils.trim(String.valueOf(value));
        for (DateTimeFormatter formatter : SUPPORTED_TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(text, formatter);
            } catch (Exception ignored) {
                // try next formatter
            }
        }
        return LocalDateTime.parse(text.replace('T', ' ').replace("Z", ""), SUPPORTED_TIME_FORMATTERS[0]);
    }

    private String toString(Object value) {
        return ObjectUtils.isEmpty(value) ? "" : String.valueOf(value);
    }
}
