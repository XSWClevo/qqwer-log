package cn.mw.loganalysis.agent.nlu;

import cn.mw.loganalysis.agent.support.SlotResult;
import lombok.Data;

/**
 * 模型 NLU 返回的意图槽位实体。
 *
 * 命名对齐导航 Agent：模型只负责建议 intent + slots，
 * 后端仍负责校验、补槽和执行。
 */
@Data
public class IntentSlotsEntity {

    private String id;

    private AgentIntent intent;

    private SlotResult slots;

    private SlotResult originalSlotResult;

    private Double confidence;

    private String reason;
}
