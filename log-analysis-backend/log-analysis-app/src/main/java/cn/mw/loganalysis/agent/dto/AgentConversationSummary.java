package cn.mw.loganalysis.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 智能助手会话摘要。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentConversationSummary {

    /**
     * 会话ID。
     * 这里直接复用现有 sessionId，避免 memory/session/history 三套 id 再分叉。
     */
    private String sessionId;

    /**
     * 会话标题。
     */
    private String title;

    /**
     * 最后一条摘要，用于左侧历史列表预览。
     */
    private String preview;

    /**
     * 当前会话绑定的数据源。
     */
    private String datasourceId;

    /**
     * 数据源显示名。
     */
    private String datasourceName;

    /**
     * 数据源类型。
     */
    private String datasourceType;

    /**
     * 消息数。
     */
    private Integer messageCount;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间。
     */
    private LocalDateTime updatedAt;

    /**
     * 最后一条消息时间。
     */
    private LocalDateTime lastMessageAt;
}
