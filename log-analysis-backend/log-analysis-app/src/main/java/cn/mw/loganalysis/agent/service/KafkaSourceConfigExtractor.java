package cn.mw.loganalysis.agent.service;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class KafkaSourceConfigExtractor implements CreateLogParserSourceConfigExtractor {

    /**
     * 判断是否处理 kafka Source 配置。
     */
    @Override
    public boolean supports(String sourceType) {
        return StringUtils.equals(sourceType, "kafka");
    }

    /**
     * 提取 Kafka bootstrap servers、topic 和消费组配置。
     */
    @Override
    public void merge(String message, Map<String, Object> config) {
        CreateLogParserSlotTextSupport.putIfNotBlank(config, "bootstrap_servers", CreateLogParserSlotTextSupport.extractKafkaBootstrap(message));
        CreateLogParserSlotTextSupport.putIfNotBlank(config, "topics", CreateLogParserSlotTextSupport.extractKafkaTopic(message));
        CreateLogParserSlotTextSupport.putIfNotBlank(config, "group_id", CreateLogParserSlotTextSupport.extractKafkaGroup(message));
    }
}
