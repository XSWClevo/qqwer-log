package cn.mw.loganalysis.agent.vectorplan;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SocketLikeSourceConfigExtractor implements CreateLogParserSourceConfigExtractor {

    /**
     * 判断是否处理 syslog 或 socket Source 配置。
     */
    @Override
    public boolean supports(String sourceType) {
        return StringUtils.equalsAny(sourceType, "syslog", "socket");
    }

    /**
     * 提取监听协议和监听地址配置。
     */
    @Override
    public void merge(String message, Map<String, Object> config) {
        CreateLogParserSlotTextSupport.putIfNotBlank(config, "syslog_mode", CreateLogParserSlotTextSupport.extractProtocol(message));
        CreateLogParserSlotTextSupport.putIfNotBlank(config, "syslog_address", CreateLogParserSlotTextSupport.extractListenAddress(message));
    }
}
