package cn.mw.loganalysis.wizard.dto;

import lombok.Data;
import java.util.List;

/**
 * 解析日志响应
 */
@Data
public class ParseLogResponse {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 错误信息
     */
    private String error;

    /**
     * 识别的格式
     */
    private String format;

    /**
     * 解析出的字段列表
     */
    private List<ParsedFieldDTO> fields;

    @Data
    public static class ParsedFieldDTO {
        /**
         * 字段名
         */
        private String name;

        /**
         * 示例值
         */
        private Object sampleValue;

        /**
         * 推断的类型
         */
        private String type;

        /**
         * 类型建议（如 IPv4）
         */
        private TypeSuggestion suggestion;
    }

    @Data
    public static class TypeSuggestion {
        /**
         * 建议的类型
         */
        private String type;

        /**
         * 建议原因
         */
        private String reason;
    }

    public static ParseLogResponse success(String format, List<ParsedFieldDTO> fields) {
        ParseLogResponse response = new ParseLogResponse();
        response.setSuccess(true);
        response.setFormat(format);
        response.setFields(fields);
        return response;
    }

    public static ParseLogResponse error(String error) {
        ParseLogResponse response = new ParseLogResponse();
        response.setSuccess(false);
        response.setError(error);
        return response;
    }
}
