package cn.mw.loganalysis.config.dto;

import lombok.Data;
import java.util.List;

/**
 * 表格配置请求DTO
 */
@Data
public class TableConfigRequest {
    /**
     * 用户名
     */
    private String username;

    /**
     * 表格类型（如：log_table）
     */
    private String tableType;

    /**
     * 可见字段列表
     */
    private List<String> visibleFields;

    /**
     * 字段顺序
     */
    private List<String> fieldOrder;
}
