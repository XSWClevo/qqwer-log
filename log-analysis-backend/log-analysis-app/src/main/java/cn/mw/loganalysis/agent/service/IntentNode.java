package cn.mw.loganalysis.agent.service;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 可执行意图节点。
 *
 * v1 只使用单节点意图，保留 next 结构是为了后续扩展多步 Agent 流程时
 * 与参考工程的 IntentNode 树形结构保持一致。
 */
@Data
@EqualsAndHashCode(callSuper = true)
class IntentNode extends IntentSlotsEntity {

    private List<IntentNode> next = new ArrayList<>();

    private boolean success;

    private boolean interrupted;

    /**
     * 将模型返回的意图槽位列表转换成可执行节点列表。
     */
    static List<IntentNode> transFromIntentSlotsEntity(List<IntentSlotsEntity> entities) {
        if (CollectionUtils.isEmpty(entities)) {
            return List.of();
        }
        return entities.stream()
                .map(IntentNode::from)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 将意图槽位实体转换成 IntentNode。
     */
    private static IntentNode from(IntentSlotsEntity entity) {
        if (entity == null) {
            return null;
        }
        if (entity instanceof IntentNode intentNode) {
            return intentNode;
        }
        IntentNode node = new IntentNode();
        node.setId(entity.getId());
        node.setIntent(entity.getIntent());
        node.setSlots(entity.getSlots());
        node.setOriginalSlotResult(entity.getOriginalSlotResult());
        node.setConfidence(entity.getConfidence());
        node.setReason(entity.getReason());
        return node;
    }
}
