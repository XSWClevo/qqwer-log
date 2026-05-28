package cn.mw.loganalysis.agent.service;

import cn.mw.loganalysis.stats.dto.AiQueryResponse;
import lombok.Builder;

import java.util.List;

/**
 * 候选竞争后的最终结果。
 */
@Builder
record SqlCandidateResult(AiQueryResponse response,
                          String candidateSource,
                          long raceMs,
                          List<String> validatedCandidates,
                          List<String> rejectedCandidates) {
}
