package cn.mw.loganalysis.vector.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * VRL 表达式执行响应
 */
@Data
public class VrlExecuteResponse {
    
    /**
     * 是否成功
     */
    private boolean success;
    
    /**
     * 错误信息
     */
    private String error;
    
    /**
     * 解析结果（JSON 对象）
     */
    private Map<String, Object> result;
    
    /**
     * 解析出的字段列表
     */
    private List<ParsedField> fields;
    
    /**
     * 原始输出
     */
    private String rawOutput;

    /**
     * 实际执行的 VRL 脚本
     */
    private String executedScript;
    
    @Data
    public static class ParsedField {
        private String name;
        private Object value;
        private String type;
        
        public ParsedField() {}
        
        public ParsedField(String name, Object value, String type) {
            this.name = name;
            this.value = value;
            this.type = type;
        }
    }
    
    public static VrlExecuteResponse success(Map<String, Object> result, List<ParsedField> fields) {
        VrlExecuteResponse response = new VrlExecuteResponse();
        response.setSuccess(true);
        response.setResult(result);
        response.setFields(fields);
        return response;
    }
    
    public static VrlExecuteResponse error(String error) {
        VrlExecuteResponse response = new VrlExecuteResponse();
        response.setSuccess(false);
        response.setError(error);
        return response;
    }
}
