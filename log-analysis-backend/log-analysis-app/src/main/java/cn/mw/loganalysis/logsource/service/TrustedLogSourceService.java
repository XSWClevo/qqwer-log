package cn.mw.loganalysis.logsource.service;

import cn.mw.loganalysis.logsource.converter.LogSourceConverter;
import cn.mw.loganalysis.logsource.dto.LogSourceDTO;
import cn.mw.loganalysis.logsource.dto.NewLogSourceNotification;
import cn.mw.loganalysis.logsource.dto.TrustLogSourceRequest;
import cn.mw.loganalysis.logsource.entity.TrustedLogSource;
import cn.mw.loganalysis.logsource.mapper.TrustedLogSourceMapper;
import cn.mw.loganalysis.logsource.repository.TrustedLogSourceRepository;
import cn.mw.loganalysis.vector.service.VectorDeploymentService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 可信任日志源服务
 * 继承 MyBatis Plus 的 ServiceImpl，提供基础 CRUD 功能
 *
 * @author Claude
 * @since 2026-01-23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrustedLogSourceService extends ServiceImpl<TrustedLogSourceMapper, TrustedLogSource> {

    private final LogSourceConverter logSourceConverter;
    private final TrustedLogSourceRepository trustedLogSourceRepository;
    private final VectorDeploymentService vectorDeploymentService;

    /**
     * 获取所有信任的日志源
     */
    public List<LogSourceDTO> getTrustedSources() {
        List<TrustedLogSource> sources = trustedLogSourceRepository.findTrustedSources();
        return logSourceConverter.toDTOList(sources);
    }

    /**
     * 获取待审核的日志源
     */
    public List<LogSourceDTO> getPendingSources() {
        List<TrustedLogSource> sources = trustedLogSourceRepository.findPendingSources();
        return logSourceConverter.toDTOList(sources);
    }

    /**
     * 获取被拉黑的日志源
     */
    public List<LogSourceDTO> getBlockedSources() {
        List<TrustedLogSource> sources = trustedLogSourceRepository.findBlockedSources();
        return logSourceConverter.toDTOList(sources);
    }

    /**
     * 根据状态查询日志源
     */
    public List<LogSourceDTO> getSourcesByStatus(String status) {
        List<TrustedLogSource> sources = trustedLogSourceRepository.findByStatus(status);
        return logSourceConverter.toDTOList(sources);
    }

    /**
     * 信任日志源
     */
    @Transactional
    public LogSourceDTO trustLogSource(TrustLogSourceRequest request, String username) {
        // 检查是否已存在
        TrustedLogSource existing = trustedLogSourceRepository.findByIp(request.getSourceIp());

        if (existing != null) {
            // 更新状态为信任
            existing.setStatus("trusted");
            existing.setTrustedAt(LocalDateTime.now());
            existing.setTrustedBy(username);
            existing.setHostname(request.getHostname());
            existing.setDescription(request.getDescription());
            existing.setRemark(request.getRemark());
            trustedLogSourceRepository.updateById(existing);
            log.info("日志源 {} 已更新为信任状态，操作人: {}", request.getSourceIp(), username);
            redeployManagedLogSourceConfigs(username);
            return logSourceConverter.toDTO(existing);
        } else {
            // 创建新记录
            TrustedLogSource newSource = new TrustedLogSource();
            newSource.setSourceIp(request.getSourceIp());
            newSource.setHostname(request.getHostname());
            newSource.setDescription(request.getDescription());
            newSource.setStatus("trusted");
            newSource.setFirstSeenAt(LocalDateTime.now());
            newSource.setLastSeenAt(LocalDateTime.now());
            newSource.setTrustedAt(LocalDateTime.now());
            newSource.setTrustedBy(username);
            newSource.setLogCount(0L);
            newSource.setRemark(request.getRemark());
            trustedLogSourceRepository.save(newSource);
            log.info("新日志源 {} 已添加到信任列表，操作人: {}", request.getSourceIp(), username);
            redeployManagedLogSourceConfigs(username);
            return logSourceConverter.toDTO(newSource);
        }
    }

    /**
     * 拉黑日志源
     */
    @Transactional
    public void blockLogSource(String sourceIp, String username) {
        TrustedLogSource source = trustedLogSourceRepository.findByIp(sourceIp);
        if (source == null) {
            throw new IllegalArgumentException("日志源不存在: " + sourceIp);
        }

        source.setStatus("blocked");
        source.setTrustedBy(username);
        trustedLogSourceRepository.updateById(source);
        log.info("日志源 {} 已拉黑，操作人: {}", sourceIp, username);
        redeployManagedLogSourceConfigs(username);
    }

    /**
     * 删除日志源
     */
    @Transactional
    public void deleteLogSource(String sourceIp) {
        TrustedLogSource source = trustedLogSourceRepository.findByIp(sourceIp);
        if (source == null) {
            throw new IllegalArgumentException("日志源不存在: " + sourceIp);
        }

        trustedLogSourceRepository.deleteById(source.getId());
        log.info("日志源 {} 已删除", sourceIp);
        redeployManagedLogSourceConfigs("system");
    }

    /**
     * 处理 Vector 发送的新 IP 通知
     * 检查 IP 是否在白名单中，如果不在则记录为 pending 状态
     *
     * @return true 表示 IP 在白名单中，false 表示需要审核
     */
    @Transactional
    public boolean handleVectorNotification(String sourceIp, String hostname, Long logCount) {
        // 检查是否已存在
        TrustedLogSource existing = trustedLogSourceRepository.findByIp(sourceIp);

        if (existing == null) {
            // 新 IP，创建 pending 记录
            TrustedLogSource newSource = new TrustedLogSource();
            newSource.setSourceIp(sourceIp);
            newSource.setHostname(hostname);
            newSource.setStatus("pending");
            newSource.setFirstSeenAt(LocalDateTime.now());
            newSource.setLastSeenAt(LocalDateTime.now());
            newSource.setLogCount(logCount != null ? logCount : 0L);
            trustedLogSourceRepository.save(newSource);
            log.warn("发现新日志源: {} ({}), 等待审核", sourceIp, hostname);
            redeployManagedLogSourceConfigs("system");
            return false; // 需要审核
        } else {
            // 更新最后活跃时间
            existing.setLastSeenAt(LocalDateTime.now());
            if (logCount != null) {
                existing.setLogCount(existing.getLogCount() + logCount);
            }
            trustedLogSourceRepository.updateById(existing);

            // 检查状态
            if ("trusted".equals(existing.getStatus())) {
                return true; // 已信任
            } else if ("blocked".equals(existing.getStatus())) {
                log.warn("拉黑的日志源尝试发送日志: {}", sourceIp);
                return false; // 已拉黑
            } else {
                return false; // pending 状态，等待审核
            }
        }
    }

    /**
     * 获取所有待审核的日志源（用于实时通知）
     */
    public List<NewLogSourceNotification> getPendingNotifications() {
        List<TrustedLogSource> pendingSources = trustedLogSourceRepository.findPendingSources();
        return pendingSources.stream()
                .map(source -> NewLogSourceNotification.builder()
                        .sourceIp(source.getSourceIp())
                        .hostname(source.getHostname())
                        .firstSeenAt(source.getFirstSeenAt())
                        .logCount(source.getLogCount())
                        .recentLogPreview(null)
                        .build())
                .collect(Collectors.toList());
    }

    /**
     * 检查 IP 是否在白名单中
     */
    public boolean isTrusted(String sourceIp) {
        TrustedLogSource source = trustedLogSourceRepository.findByIp(sourceIp);
        return source != null && "trusted".equals(source.getStatus());
    }

    /**
     * 检查 IP 是否被拉黑
     */
    public boolean isBlocked(String sourceIp) {
        TrustedLogSource source = trustedLogSourceRepository.findByIp(sourceIp);
        return source != null && "blocked".equals(source.getStatus());
    }

    private void redeployManagedLogSourceConfigs(String username) {
        vectorDeploymentService.redeployManagedLogSourceConfigs(username);
    }
}
