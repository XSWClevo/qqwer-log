package cn.mw.loganalysis.agent.service;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class FileSourceConfigExtractor implements CreateLogParserSourceConfigExtractor {

    /**
     * 判断是否处理 file Source 配置。
     */
    @Override
    public boolean supports(String sourceType) {
        return StringUtils.equals(sourceType, "file");
    }

    /**
     * 提取文件路径和 read_from 读取位置配置。
     */
    @Override
    public void merge(String message, Map<String, Object> config) {
        CreateLogParserSlotTextSupport.putIfNotBlank(config, "include", CreateLogParserSlotTextSupport.extractFilePath(message));
        String readFrom = CreateLogParserSlotTextSupport.extractReadFrom(message);
        if (StringUtils.isNotBlank(readFrom)) {
            config.put("read_from", readFrom);
        } else {
            config.putIfAbsent("read_from", "beginning");
        }
    }
}
