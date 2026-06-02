package cn.mw.loganalysis.agent.vectorplan;

import cn.mw.loganalysis.vector.entity.ConfigComponent;

public record CreateLogParserSlotContext(AgentTaskFrame frame,
                                  String message,
                                  ConfigComponent currentSink) {
}
