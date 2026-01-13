package cn.mw.loganalysis.config.dto;

import lombok.Data;

import java.util.List;

/**
 * 用户字段配置响应DTO
 */
@Data
public class FieldConfigResponse {
    /**
     * 配置类型
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

    /**
     * 所有可用字段
     */
    private List<String> availableFields;
}
