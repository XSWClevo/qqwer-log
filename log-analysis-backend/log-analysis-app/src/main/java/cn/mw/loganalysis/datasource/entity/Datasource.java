package cn.mw.loganalysis.datasource.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据源实体
 */
@Data
@TableName("datasources")
public class Datasource {

    /**
     * 数据源ID（UUID）
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 数据源名称（唯一）
     */
    @TableField("name")
    private String name;

    /**
     * 数据源类型: clickhouse, elasticsearch, postgresql, mysql, loki
     */
    @TableField("type")
    private String type;

    /**
     * 主机地址
     */
    @TableField("host")
    private String host;

    /**
     * 端口号
     */
    @TableField("port")
    private Integer port;

    /**
     * 数据库名称
     */
    @TableField("database_name")
    private String databaseName;

    /**
     * 用户名
     */
    @TableField("username")
    private String username;

    /**
     * 密码（加密存储）
     */
    @TableField("password")
    private String password;

    /**
     * 是否启用 SSL
     */
    @TableField("ssl_enabled")
    private Boolean sslEnabled;

    /**
     * 额外连接参数（JSON 格式）
     */
    @TableField("connection_params")
    private String connectionParams;

    /**
     * 数据源描述
     */
    @TableField("description")
    private String description;

    /**
     * 状态: active, inactive, error
     */
    @TableField("status")
    private String status;

    /**
     * 最后检查时间
     */
    @TableField("last_check_time")
    private LocalDateTime lastCheckTime;

    /**
     * 最后检查状态: success, failed
     */
    @TableField("last_check_status")
    private String lastCheckStatus;

    /**
     * 最后检查消息
     */
    @TableField("last_check_message")
    private String lastCheckMessage;

    /**
     * 创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /**
     * 创建人
     */
    @TableField("created_by")
    private Long createdBy;

    /**
     * 更新人
     */
    @TableField("updated_by")
    private Long updatedBy;
}
