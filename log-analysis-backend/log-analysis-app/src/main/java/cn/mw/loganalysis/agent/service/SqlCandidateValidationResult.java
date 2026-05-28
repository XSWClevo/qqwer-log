package cn.mw.loganalysis.agent.service;

/**
 * SQL 候选校验结果。
 */
record SqlCandidateValidationResult(boolean valid, String sql, String reason) {

    static SqlCandidateValidationResult valid(String sql) {
        return new SqlCandidateValidationResult(true, sql, null);
    }

    static SqlCandidateValidationResult invalid(String reason) {
        return new SqlCandidateValidationResult(false, null, reason);
    }
}
