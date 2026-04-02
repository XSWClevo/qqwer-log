package cn.mw.loganalysis.logcategory.entity;

import cn.mw.loganalysis.common.handler.PostgresJsonbTypeHandler;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 日志分类注册表
 */
@Data
@TableName(value = "log_category_registry", autoResultMap = true)
public class LogCategoryRegistry implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String categoryCode;

    private String categoryName;

    private String datasourceId;

    private String tableName;

    private String databaseName;

    private String timeField;

    private String messageField;

    private String rawField;

    private String severityField;

    private String sourceIpField;

    private String appnameField;

    private String hostnameField;

    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private Map<String, String> extraMapping;

    private Boolean enabled;

    private Integer priority;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
