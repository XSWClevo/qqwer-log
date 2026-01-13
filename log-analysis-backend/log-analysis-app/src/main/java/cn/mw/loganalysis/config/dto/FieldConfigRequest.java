package cn.mw.loganalysis.config.dto;

import lombok.Data;

import java.util.List;

/**
 * 用户字段配置请求DTO
 */
@Data
public class FieldConfigRequest {
    /**
     * 配置类型（如：log_list）
     */
    private String configType;

    /**
     * 已选择的字段列表
     */
    private List<String> selectedFields;

    /**
     * 字段顺序
     */
    private List<String> fieldOrder;
}
