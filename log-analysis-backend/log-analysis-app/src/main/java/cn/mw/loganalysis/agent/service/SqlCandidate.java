package cn.mw.loganalysis.agent.service;

import lombok.Builder;

import java.util.Map;

/**
 * Text2SQL 候选 SQL。
 */
@Builder
record SqlCandidate(String source,
                    String sql,
                    String resultType,
                    double confidence,
                    long generationTimeMs,
                    Map<String, Object> metadata) {

    SqlCandidate {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
