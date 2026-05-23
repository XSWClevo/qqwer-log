package cn.mw.loganalysis.attack.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * ClickHouse attack_classifications 结果记录。
 */
@Data
@TableName("attack_classifications")
public class AttackClassificationRecord {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String classificationKey;

    private String datasourceType;

    private String datasourceId;

    private String databaseName;

    private String tableName;

    private String indexName;

    private String logFingerprint;

    private LocalDateTime logTimestamp;

    private String sourceIp;

    private String hostname;

    private String message;

    private String raw;

    private String attackType;

    private String attackSubType;

    private String severity;

    private Float confidence;

    private String ruleId;

    private String ruleName;

    private String reason;

    private String mitreTactic;

    private String mitreTechnique;

    private String status;

    private LocalDateTime classifiedAt;
}
