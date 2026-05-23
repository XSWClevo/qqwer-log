package cn.mw.loganalysis.attack.entity;

import cn.mw.loganalysis.common.handler.PostgresJsonbTypeHandler;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 攻击分类日志数据集。
 * 一条记录表示一个可参与攻击分类的动态日志表或索引。
 */
@Data
@TableName(value = "attack_log_datasets", autoResultMap = true)
public class AttackLogDataset {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String datasourceType;

    private String datasourceId;

    private String databaseName;

    private String tableName;

    private String indexName;

    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private Map<String, String> fieldMapping;

    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private Map<String, Object> capabilities;

    private Boolean enabled;

    private LocalDateTime scanCursorTimestamp;

    private String scanCursorFingerprint;

    private Integer batchSize;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
