package cn.mw.loganalysis.agent.service;

import cn.mw.loganalysis.vector.entity.ConfigComponent;

record CreateLogParserSlotContext(AgentTaskFrame frame,
                                  String message,
                                  ConfigComponent currentSink) {
}
