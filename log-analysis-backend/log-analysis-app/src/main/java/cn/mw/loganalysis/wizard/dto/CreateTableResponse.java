package cn.mw.loganalysis.wizard.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建表响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTableResponse {

    /**
     * 是否成功
     */
    private Boolean success;

    /**
     * 错误信息
     */
    private String error;

    /**
     * 创建的 Remap Transform 组件 ID
     */
    private String remapComponentId;

    /**
     * 创建的 ClickHouse Sink 组件 ID
     */
    private String sinkComponentId;

    /**
     * 表名
     */
    private String tableName;

    public static CreateTableResponse success(String tableName, String remapComponentId, String sinkComponentId) {
        return CreateTableResponse.builder()
                .success(true)
                .tableName(tableName)
                .remapComponentId(remapComponentId)
                .sinkComponentId(sinkComponentId)
                .build();
    }

    public static CreateTableResponse error(String error) {
        return CreateTableResponse.builder()
                .success(false)
                .error(error)
                .build();
    }
}
