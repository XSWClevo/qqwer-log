package cn.mw.loganalysis.agent.service;

import java.util.List;
import java.util.Map;

record Text2SqlQueryShape(String queryResultType, Object rawResult, List<Map<String, Object>> rows) {
}
