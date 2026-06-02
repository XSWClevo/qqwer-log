package cn.mw.loganalysis.agent.vectorplan;

import org.springframework.core.Ordered;

import java.util.Map;

public interface CreateLogParserSourceConfigExtractor extends Ordered {

    /**
     * 判断该提取器是否支持当前 Source 类型。
     */
    boolean supports(String sourceType);

    /**
     * 从用户消息中提取 Source 配置并合并到配置 Map。
     */
    void merge(String message, Map<String, Object> config);

    /**
     * 返回 Source 配置提取顺序，数值越小越先执行。
     */
    @Override
    default int getOrder() {
        return 0;
    }
}
