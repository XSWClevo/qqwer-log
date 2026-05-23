package cn.mw.loganalysis.attack.reader;

import cn.mw.loganalysis.attack.entity.AttackLogDataset;
import cn.mw.loganalysis.attack.model.NormalizedLogRecord;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 攻击分类日志读取适配器。
 * 后续兼容 ES 时新增 Elasticsearch 实现即可，不改规则和结果表。
 */
public interface AttackLogDatasetReader {

    boolean supports(String datasourceType);

    List<NormalizedLogRecord> read(AttackLogDataset dataset, LocalDateTime startTime, LocalDateTime endTime, int limit);
}
