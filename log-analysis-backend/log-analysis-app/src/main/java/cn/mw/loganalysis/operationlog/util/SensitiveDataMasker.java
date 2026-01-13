package cn.mw.loganalysis.operationlog.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 敏感数据脱敏工具类
 * <p>
 * 自动脱敏敏感字段，防止日志中泄露密码、Token 等信息
 * </p>
 *
 * @author Claude
 * @since 2026-01-07
 */
@Slf4j
public class SensitiveDataMasker {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 默认敏感字段 (自动脱敏)
     */
    private static final Set<String> DEFAULT_SENSITIVE_FIELDS = new HashSet<>(Arrays.asList(
        "password",
        "passwordHash",
        "passwd",
        "pwd",
        "token",
        "accessToken",
        "refreshToken",
        "secret",
        "secretKey",
        "apiKey",
        "apiSecret",
        "privateKey",
        "idCard",
        "bankCard",
        "phone",
        "mobile",
        "email"
    ));

    /**
     * 完全脱敏 (显示为 *****)
     */
    private static final String FULL_MASK = "******";

    /**
     * 脱敏对象 (支持 Map, POJO, JSON 字符串)
     *
     * @param data             原始数据
     * @param customSensitiveFields 自定义敏感字段
     * @return 脱敏后的数据
     */
    public static Object mask(Object data, String[] customSensitiveFields) {
        if (data == null) {
            return null;
        }

        Set<String> sensitiveFields = new HashSet<>(DEFAULT_SENSITIVE_FIELDS);
        if (customSensitiveFields != null && customSensitiveFields.length > 0) {
            sensitiveFields.addAll(Arrays.asList(customSensitiveFields));
        }

        try {
            // 转换为 JsonNode 进行处理
            JsonNode jsonNode = OBJECT_MAPPER.valueToTree(data);
            JsonNode maskedNode = maskJsonNode(jsonNode, sensitiveFields);

            // 返回 Map (便于存储到 JSONB)
            return OBJECT_MAPPER.convertValue(maskedNode, Map.class);
        } catch (Exception e) {
            log.warn("Failed to mask sensitive data, returning original data", e);
            return data;
        }
    }

    /**
     * 递归脱敏 JsonNode
     */
    private static JsonNode maskJsonNode(JsonNode node, Set<String> sensitiveFields) {
        if (node == null || node.isNull()) {
            return node;
        }

        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node;
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();

            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                String fieldName = field.getKey();
                JsonNode fieldValue = field.getValue();

                // 判断是否为敏感字段 (忽略大小写)
                if (isSensitiveField(fieldName, sensitiveFields)) {
                    objectNode.put(fieldName, maskValue(fieldValue));
                } else if (fieldValue.isObject() || fieldValue.isArray()) {
                    // 递归处理嵌套对象和数组
                    objectNode.set(fieldName, maskJsonNode(fieldValue, sensitiveFields));
                }
            }
            return objectNode;
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                JsonNode element = node.get(i);
                if (element.isObject() || element.isArray()) {
                    ((com.fasterxml.jackson.databind.node.ArrayNode) node).set(i, maskJsonNode(element, sensitiveFields));
                }
            }
            return node;
        }

        return node;
    }

    /**
     * 判断是否为敏感字段 (忽略大小写)
     */
    private static boolean isSensitiveField(String fieldName, Set<String> sensitiveFields) {
        return sensitiveFields.stream()
            .anyMatch(sensitive -> sensitive.equalsIgnoreCase(fieldName));
    }

    /**
     * 脱敏单个值
     */
    private static String maskValue(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }

        String strValue = value.asText();

        // 完全脱敏
        return FULL_MASK;
    }

    /**
     * Token 类脱敏 (显示前3后3)
     * <p>
     * 示例: "abc1234567xyz" -> "abc...xyz"
     * </p>
     */
    public static String maskToken(String token) {
        if (token == null || token.length() <= 6) {
            return FULL_MASK;
        }
        return token.substring(0, 3) + "..." + token.substring(token.length() - 3);
    }

    /**
     * 手机号脱敏 (显示前3后4)
     * <p>
     * 示例: "13812345678" -> "138****5678"
     * </p>
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() != 11) {
            return FULL_MASK;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    /**
     * 邮箱脱敏 (显示前2位和域名)
     * <p>
     * 示例: "user@example.com" -> "us***@example.com"
     * </p>
     */
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return FULL_MASK;
        }
        String[] parts = email.split("@");
        if (parts[0].length() <= 2) {
            return FULL_MASK + "@" + parts[1];
        }
        return parts[0].substring(0, 2) + "***@" + parts[1];
    }

    /**
     * 身份证脱敏 (显示前6后4)
     * <p>
     * 示例: "110101199001011234" -> "110101********1234"
     * </p>
     */
    public static String maskIdCard(String idCard) {
        if (idCard == null || idCard.length() < 10) {
            return FULL_MASK;
        }
        return idCard.substring(0, 6) + "********" + idCard.substring(idCard.length() - 4);
    }
}
