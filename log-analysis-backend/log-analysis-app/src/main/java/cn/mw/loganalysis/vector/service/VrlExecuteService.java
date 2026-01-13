package cn.mw.loganalysis.vector.service;

import cn.mw.loganalysis.vector.dto.VrlExecuteRequest;
import cn.mw.loganalysis.vector.dto.VrlExecuteResponse;
import cn.mw.loganalysis.vector.dto.VrlExecuteResponse.ParsedField;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * VRL 表达式执行服务
 * 使用 vector vrl 命令执行 VRL 表达式
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VrlExecuteService {
    
    private final ObjectMapper objectMapper;
    
    /**
     * 执行 VRL 表达式解析日志
     */
    public VrlExecuteResponse execute(VrlExecuteRequest request) {
        try {
            // 构建 VRL 脚本
            String vrlScript = buildVrlScript(request);
            log.info("执行 VRL 脚本: {}", vrlScript);
            
            // 创建临时文件存储输入数据
            Path inputFile = Files.createTempFile("vrl_input_", ".json");
            Path programFile = Files.createTempFile("vrl_program_", ".vrl");
            
            try {
                // 写入输入数据（JSON 格式）
                Map<String, Object> inputEvent = new HashMap<>();
                inputEvent.put("message", request.getLogSample());
                Files.writeString(inputFile, objectMapper.writeValueAsString(inputEvent));
                
                // 写入 VRL 程序
                Files.writeString(programFile, vrlScript);
                
                // 执行 vector vrl 命令
                ProcessBuilder pb = new ProcessBuilder(
                    "vector", "vrl",
                    "--input", inputFile.toString(),
                    "--program", programFile.toString(),
                    "--print-object"
                );
                pb.redirectErrorStream(false);
                
                Process process = pb.start();
                
                // 读取输出
                String stdout = readStream(process.getInputStream());
                String stderr = readStream(process.getErrorStream());
                
                boolean finished = process.waitFor(30, TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    return VrlExecuteResponse.error("执行超时");
                }
                
                int exitCode = process.exitValue();
                log.info("VRL 执行完成, exitCode={}, stdout={}, stderr={}", exitCode, stdout, stderr);
                
                if (exitCode != 0) {
                    // 解析错误信息
                    String errorMsg = stderr.isEmpty() ? stdout : stderr;
                    return VrlExecuteResponse.error(parseErrorMessage(errorMsg));
                }
                
                // 解析输出结果
                return parseOutput(stdout);
                
            } finally {
                // 清理临时文件
                Files.deleteIfExists(inputFile);
                Files.deleteIfExists(programFile);
            }
            
        } catch (Exception e) {
            log.error("执行 VRL 失败", e);
            return VrlExecuteResponse.error("执行失败: " + e.getMessage());
        }
    }
    
    /**
     * 构建 VRL 脚本
     * 
     * 解析策略：
     * 1. 先尝试用 parse_syslog 解析原始消息，提取 syslog 元数据和 message
     * 2. 如果 syslog 解析成功，使用提取的 message 进行后续解析
     * 3. 如果 syslog 解析失败，直接使用原始 message 进行解析
     * 4. 使用 ?? 运算符实现链式解析尝试
     * 5. 最后将所有 timestamp 类型转为字符串，确保输出是标准 JSON
     */
    private String buildVrlScript(VrlExecuteRequest request) {
        StringBuilder script = new StringBuilder();
        
        // 第一步：尝试 syslog 预解析，提取真正的 message
        script.append("# 尝试 syslog 预解析\n");
        script.append("raw_message = .message\n");
        script.append("syslog_result = parse_syslog(.message) ?? null\n");
        script.append("if syslog_result != null {\n");
        script.append("  . = merge!(., syslog_result)\n");
        script.append("}\n");
        
        // 第二步：根据用户选择的解析方式解析 target_message
        script.append("# 使用用户选择的方式解析消息内容\n");
        
        switch (request.getParseMethod()) {
            case "parse_json":
                // 链式尝试：JSON -> KV -> 原样保留
                script.append("parsed = parse_json(.message) ?? parse_key_value(.message) ?? null\n");
                script.append("if parsed != null {\n");
                script.append("  . = merge!(., parsed)\n");
                script.append("}\n");
                break;
                
            case "parse_syslog":
                // 纯 syslog 解析，直接使用预解析结果
                script.append("if syslog_result != null {\n");
                script.append("  . = merge!(., syslog_result)\n");
                script.append("} else {\n");
                script.append("  .parse_error = \"syslog 解析失败\"\n");
                script.append("}\n");
                break;
                
            case "parse_regex":
                if (request.getRegexPattern() == null || request.getRegexPattern().isEmpty()) {
                    throw new IllegalArgumentException("正则表达式不能为空");
                }
                String pattern = request.getRegexPattern();
                script.append(String.format("parsed = parse_regex(.message, r'%s') ?? null\n", pattern));
                script.append("if parsed != null {\n");
                script.append("  . = merge!(., parsed)\n");
                script.append("} else {\n");
                script.append("  .parse_error = \"正则解析失败\"\n");
                script.append("}\n");
                break;
                
            case "parse_kv":
            case "parse_key_value":
                script.append("parsed = parse_key_value(.message) ?? null\n");
                script.append("if parsed != null {\n");
                script.append("  . = merge!(., parsed)\n");
                script.append("}\n");
                break;
                
            case "parse_grok":
                if (request.getGrokPattern() == null || request.getGrokPattern().isEmpty()) {
                    throw new IllegalArgumentException("Grok 模式不能为空");
                }
                script.append(String.format("parsed = parse_grok(target_message, \"%%{%s}\") ?? null\n", request.getGrokPattern()));
                script.append("if parsed != null {\n");
                script.append("  . = merge!(., parsed)\n");
                script.append("} else {\n");
                script.append("  .parse_error = \"Grok 解析失败\"\n");
                script.append("}\n");
                break;
                
            case "custom":
                if (request.getCustomVrl() == null || request.getCustomVrl().isEmpty()) {
                    throw new IllegalArgumentException("自定义 VRL 脚本不能为空");
                }
                script.append(request.getCustomVrl()).append("\n");
                break;
                
            default:
                throw new IllegalArgumentException("不支持的解析方式: " + request.getParseMethod());
        }
        

        // 将 timestamp 类型转为字符串，确保输出是标准 JSON
        script.append("\n# 将 timestamp 类型转为 ISO8601 字符串\n");
        script.append("ts_keys = []\n");
        script.append("for_each(keys(.)) -> |_index, key| {\n");
        script.append("  val = get!(., [key])\n");
        script.append("  if is_timestamp(val) {\n");
        script.append("    ts_keys = push(ts_keys, key)\n");
        script.append("  }\n");
        script.append("}\n");
        script.append("for_each(ts_keys) -> |_index, key| {\n");
        script.append("  val = get!(., [key])\n");
        script.append("  . = set!(., [key], format_timestamp!(val, \"%Y-%m-%dT%H:%M:%S%:z\"))\n");
        script.append("}\n");
        
        return script.toString();
    }
    
    /**
     * 解析输出结果
     */
    private VrlExecuteResponse parseOutput(String output) {
        try {
            // 清理输出（移除可能的 ANSI 颜色代码）
            output = output.replaceAll("\u001B\\[[;\\d]*m", "").trim();
            
            if (output.isEmpty()) {
                return VrlExecuteResponse.error("解析结果为空");
            }
            
            // 解析 JSON 输出
            Map<String, Object> result = objectMapper.readValue(output, 
                new TypeReference<Map<String, Object>>() {});
            
            // 提取字段列表
            List<ParsedField> fields = new ArrayList<>();
            for (Map.Entry<String, Object> entry : result.entrySet()) {
                String name = entry.getKey();
                Object value = entry.getValue();
                String type = detectType(value);
                fields.add(new ParsedField(name, value, type));
            }
            
            // 按字段名排序
            fields.sort(Comparator.comparing(ParsedField::getName));
            
            VrlExecuteResponse response = VrlExecuteResponse.success(result, fields);
            response.setRawOutput(output);
            return response;
            
        } catch (Exception e) {
            log.error("解析 VRL 输出失败: {}", output, e);
            return VrlExecuteResponse.error("解析输出失败: " + e.getMessage());
        }
    }
    
    /**
     * 检测值类型
     */
    private String detectType(Object value) {
        if (value == null) {
            return "null";
        } else if (value instanceof String) {
            return "string";
        } else if (value instanceof Integer || value instanceof Long) {
            return "integer";
        } else if (value instanceof Double || value instanceof Float) {
            return "float";
        } else if (value instanceof Boolean) {
            return "boolean";
        } else if (value instanceof List) {
            return "array";
        } else if (value instanceof Map) {
            return "object";
        } else {
            return "unknown";
        }
    }
    
    /**
     * 解析错误信息
     */
    private String parseErrorMessage(String error) {
        // 简化错误信息
        if (error.contains("error[E")) {
            // 提取主要错误信息
            int start = error.indexOf("error[E");
            int end = error.indexOf("\n", start);
            if (end > start) {
                return error.substring(start, end).trim();
            }
        }
        return error.length() > 500 ? error.substring(0, 500) + "..." : error;
    }
    
    /**
     * 读取流内容
     */
    private String readStream(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString().trim();
    }
}
