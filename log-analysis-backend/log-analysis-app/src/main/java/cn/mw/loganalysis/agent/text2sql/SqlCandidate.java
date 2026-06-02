package cn.mw.loganalysis.agent.text2sql;

import lombok.Builder;

import java.util.Map;

/**
 * Text2SQL 候选 SQL。
 */
@Builder
public record SqlCandidate(String source,
                    String sql,
                    String resultType,
                    double confidence,
                    long generationTimeMs,
                    Map<String, Object> metadata) {

    public SqlCandidate {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
