package cn.mw.loganalysis.agent.text2sql;

import java.util.List;
import java.util.Map;

public record Text2SqlQueryShape(String queryResultType, Object rawResult, List<Map<String, Object>> rows) {
}
