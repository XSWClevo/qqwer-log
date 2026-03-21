package cn.mw.loganalysis.logsource.repository;

import cn.mw.loganalysis.logsource.entity.TrustedLogSource;
import cn.mw.loganalysis.logsource.mapper.TrustedLogSourceMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

/**
 * 可信日志源仓储。
 */
@Repository
@RequiredArgsConstructor
public class TrustedLogSourceRepository {

    private final TrustedLogSourceMapper trustedLogSourceMapper;

    public TrustedLogSource findByIp(String sourceIp) {
        if (StringUtils.isBlank(sourceIp)) {
            return null;
        }

        return trustedLogSourceMapper.selectOne(
                Wrappers.<TrustedLogSource>lambdaQuery()
                        .eq(TrustedLogSource::getSourceIp, StringUtils.trim(sourceIp))
        );
    }

    public List<TrustedLogSource> findTrustedSources() {
        return trustedLogSourceMapper.selectList(
                Wrappers.<TrustedLogSource>lambdaQuery()
                        .eq(TrustedLogSource::getStatus, "trusted")
                        .orderByDesc(TrustedLogSource::getLastSeenAt)
        );
    }

    public List<TrustedLogSource> findPendingSources() {
        return trustedLogSourceMapper.selectList(
                Wrappers.<TrustedLogSource>lambdaQuery()
                        .eq(TrustedLogSource::getStatus, "pending")
                        .orderByDesc(TrustedLogSource::getFirstSeenAt)
        );
    }

    public List<TrustedLogSource> findBlockedSources() {
        return trustedLogSourceMapper.selectList(
                Wrappers.<TrustedLogSource>lambdaQuery()
                        .eq(TrustedLogSource::getStatus, "blocked")
                        .orderByDesc(TrustedLogSource::getUpdatedAt)
        );
    }

    public List<TrustedLogSource> findByStatus(String status) {
        if (StringUtils.isBlank(status)) {
            return Collections.emptyList();
        }

        return trustedLogSourceMapper.selectList(
                Wrappers.<TrustedLogSource>lambdaQuery()
                        .eq(TrustedLogSource::getStatus, StringUtils.trim(status))
                        .orderByDesc(TrustedLogSource::getLastSeenAt)
        );
    }

    public void save(TrustedLogSource source) {
        if (source == null) {
            return;
        }
        trustedLogSourceMapper.insert(source);
    }

    public void updateById(TrustedLogSource source) {
        if (source == null || source.getId() == null) {
            return;
        }
        trustedLogSourceMapper.updateById(source);
    }

    public void deleteById(Long id) {
        if (id == null) {
            return;
        }
        trustedLogSourceMapper.deleteById(id);
    }
}
