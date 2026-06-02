package cn.mw.loganalysis.agent.text2sql;

import cn.mw.loganalysis.stats.dto.AiQueryResponse;
import lombok.Builder;

import java.util.List;

/**
 * 候选竞争后的最终结果。
 */
@Builder
public record SqlCandidateResult(AiQueryResponse response,
                          String candidateSource,
                          long raceMs,
                          List<String> validatedCandidates,
                          List<String> rejectedCandidates) {
}
