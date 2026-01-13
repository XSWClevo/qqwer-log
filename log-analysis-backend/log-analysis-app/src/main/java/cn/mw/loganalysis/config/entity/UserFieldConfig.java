package cn.mw.loganalysis.config.entity;

import cn.mw.loganalysis.common.handler.PostgresJsonbTypeHandler;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户字段配置实体
 */
@Data
@TableName(value = "user_field_config", autoResultMap = true)
public class UserFieldConfig {
    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 配置类型（如：log_list）
     */
    private String configType;

    /**
     * 已选择的字段列表（JSON数组）
     */
    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private List<String> selectedFields;

    /**
     * 字段顺序（JSON数组）
     */
    @TableField(typeHandler = PostgresJsonbTypeHandler.class)
    private List<String> fieldOrder;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
