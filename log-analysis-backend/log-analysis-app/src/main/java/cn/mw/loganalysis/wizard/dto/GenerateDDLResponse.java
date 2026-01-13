package cn.mw.loganalysis.wizard.dto;

import lombok.Data;

import java.util.Map;

/**
 * 生成 DDL 响应
 */
@Data
public class GenerateDDLResponse {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 错误信息
     */
    private String error;

    /**
     * 生成的 DDL
     */
    private String ddl;

    /**
     * 使用的配置
     */
    private Map<String, String> config;

    public static GenerateDDLResponse success(String ddl, Map<String, String> config) {
        GenerateDDLResponse response = new GenerateDDLResponse();
        response.setSuccess(true);
        response.setDdl(ddl);
        response.setConfig(config);
        return response;
    }

    public static GenerateDDLResponse error(String error) {
        GenerateDDLResponse response = new GenerateDDLResponse();
        response.setSuccess(false);
        response.setError(error);
        return response;
    }
}
