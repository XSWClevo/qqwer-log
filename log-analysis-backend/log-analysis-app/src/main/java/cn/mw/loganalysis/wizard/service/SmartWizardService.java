package cn.mw.loganalysis.wizard.service;

import cn.mw.loganalysis.vector.dto.VrlExecuteRequest;
import cn.mw.loganalysis.vector.dto.VrlExecuteResponse;
import cn.mw.loganalysis.vector.service.VrlExecuteService;
import cn.mw.loganalysis.wizard.dto.ParseLogResponse;
import cn.mw.loganalysis.wizard.dto.ParseLogResponse.ParsedFieldDTO;
import cn.mw.loganalysis.wizard.dto.ParseLogResponse.TypeSuggestion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 智能向导服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmartWizardService {

    private final VrlExecuteService vrlExecuteService;
    private final FieldTypeInferenceService fieldTypeInferenceService;

    /**
     * 解析日志样本
     */
    public ParseLogResponse parseLog(VrlExecuteRequest request) {
        // 使用 VRL 服务解析日志
        VrlExecuteResponse vrlResponse = vrlExecuteService.execute(request);

        if (!vrlResponse.isSuccess()) {
            return ParseLogResponse.error(vrlResponse.getError());
        }

        // 转换字段并进行类型推断
        List<ParsedFieldDTO> fields = new ArrayList<>();
        for (VrlExecuteResponse.ParsedField field : vrlResponse.getFields()) {
            ParsedFieldDTO dto = new ParsedFieldDTO();
            dto.setName(field.getName());
            dto.setSampleValue(field.getValue());

            // 推断类型
            String inferredType = fieldTypeInferenceService.inferType(field.getValue());
            dto.setType(inferredType);

            // 获取类型建议
            String suggestion = fieldTypeInferenceService.getTypeSuggestion(field.getValue());
            if (suggestion != null) {
                TypeSuggestion typeSuggestion = new TypeSuggestion();
                typeSuggestion.setType(suggestion);
                typeSuggestion.setReason(fieldTypeInferenceService.getSuggestionReason(suggestion));
                dto.setSuggestion(typeSuggestion);
            }

            fields.add(dto);
        }

        // 识别格式
        String format = identifyFormat(request.getParseMethod());

        return ParseLogResponse.success(format, fields, vrlResponse.getExecutedScript());
    }

    /**
     * 识别日志格式
     */
    private String identifyFormat(String parseMethod) {
        switch (parseMethod) {
            case "parse_json":
                return "JSON";
            case "auto":
                return "自动识别";
            case "parse_syslog":
                return "Syslog RFC 5424";
            case "parse_kv":
            case "parse_key_value":
                return "Key-Value";
            case "parse_regex":
                return "自定义正则";
            case "parse_grok":
                return "Grok";
            case "custom":
                return "自定义 VRL";
            default:
                return "未知格式";
        }
    }
}
