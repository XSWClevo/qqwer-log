package cn.mw.loganalysis.agent.service;

import cn.mw.loganalysis.agent.dto.AgentChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * 把依赖上下文的短消息补全成可识别消息。
 */
@Slf4j
@Component
public class MessageHistoryContextEnhancer implements AgentContextEnhancer {

    /**
     * 指定历史消息增强在数据源加载前执行。
     */
    @Override
    public int getOrder() {
        return 10;
    }

    /**
     * 规范化用户消息，并在短指代消息中拼接最近一条用户历史。
     */
    @Override
    public void enhance(AgentRuntimeContext context) {
        String normalizedMessage = AgentToolSupport.normalizeText(context.getRequest().getMessage());
        context.setNormalizedMessage(normalizedMessage);
        context.setEffectiveMessage(enrichMessageWithHistory(normalizedMessage, context.getRequest().getHistory()));
    }

    /**
     * 如果当前消息依赖上下文，则用最近一条用户消息补全。
     */
    private String enrichMessageWithHistory(String message, List<AgentChatMessage> history) {
        if (StringUtils.isBlank(message) || !looksContextDependent(message)) {
            return message;
        }

        String lastUserMessage = latestUserMessage(history);
        if (StringUtils.isBlank(lastUserMessage)) {
            return message;
        }
        return lastUserMessage + " " + message;
    }

    /**
     * 判断消息是否像“继续/这个/改成”这类依赖上下文的短表达。
     */
    private boolean looksContextDependent(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        if (message.length() <= 12) {
            return true;
        }
        return AgentToolSupport.containsAny(lower, "再", "继续", "那", "这个", "这些", "呢", "同样", "也", "改成", "换成", "刚才", "上一个");
    }

    /**
     * 从请求历史中找到最近一条用户消息。
     */
    private String latestUserMessage(List<AgentChatMessage> history) {
        if (CollectionUtils.isEmpty(history)) {
            return null;
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            AgentChatMessage message = history.get(i);
            if (message != null
                    && StringUtils.equalsIgnoreCase(message.getRole(), "user")
                    && StringUtils.isNotBlank(message.getContent())) {
                return AgentToolSupport.normalizeText(message.getContent());
            }
        }
        return null;
    }
}
