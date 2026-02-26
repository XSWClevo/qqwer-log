package cn.mw.loganalysis.logsource.controller;

import cn.mw.loganalysis.common.response.Result;
import cn.mw.loganalysis.logsource.dto.LogSourceDTO;
import cn.mw.loganalysis.logsource.dto.NewLogSourceNotification;
import cn.mw.loganalysis.logsource.dto.TrustLogSourceRequest;
import cn.mw.loganalysis.logsource.dto.VectorNewIpNotification;
import cn.mw.loganalysis.logsource.service.TrustedLogSourceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 可信任日志源管理 Controller
 *
 * @author Claude
 * @since 2026-01-23
 */
@Slf4j
@RestController
@RequestMapping("/api/log-sources")
@RequiredArgsConstructor
public class TrustedLogSourceController {

    private final TrustedLogSourceService trustedLogSourceService;

    /**
     * 获取所有信任的日志源
     */
    @PostMapping("/trusted")
    public Result<List<LogSourceDTO>> getTrustedSources() {
        return Result.success(trustedLogSourceService.getTrustedSources());
    }

    /**
     * 获取待审核的日志源
     */
    @PostMapping("/pending")
    public Result<List<LogSourceDTO>> getPendingSources() {
        return Result.success(trustedLogSourceService.getPendingSources());
    }

    /**
     * 获取被拉黑的日志源
     */
    @PostMapping("/blocked")
    public Result<List<LogSourceDTO>> getBlockedSources() {
        return Result.success(trustedLogSourceService.getBlockedSources());
    }

    /**
     * 根据状态查询日志源
     */
    @PostMapping("/list")
    public Result<List<LogSourceDTO>> getSourcesByStatus(@RequestBody(required = false) StatusRequest request) {
        String status = request != null ? request.getStatus() : null;
        if (status == null || status.isEmpty()) {
            // 返回所有状态
            List<LogSourceDTO> all = trustedLogSourceService.list().stream()
                    .map(source -> LogSourceDTO.builder()
                            .id(source.getId())
                            .sourceIp(source.getSourceIp())
                            .hostname(source.getHostname())
                            .description(source.getDescription())
                            .status(source.getStatus())
                            .firstSeenAt(source.getFirstSeenAt())
                            .lastSeenAt(source.getLastSeenAt())
                            .trustedAt(source.getTrustedAt())
                            .trustedBy(source.getTrustedBy())
                            .logCount(source.getLogCount())
                            .remark(source.getRemark())
                            .build())
                    .toList();
            return Result.success(all);
        }
        return Result.success(trustedLogSourceService.getSourcesByStatus(status));
    }

    /**
     * 信任日志源
     */
    @PostMapping("/trust")
    public Result<LogSourceDTO> trustLogSource(
            @Valid @RequestBody TrustLogSourceRequest request,
            Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "system";
        LogSourceDTO result = trustedLogSourceService.trustLogSource(request, username);
        return Result.success(result);
    }

    /**
     * 拉黑日志源
     */
    @PostMapping("/block")
    public Result<Void> blockLogSource(
            @RequestBody BlockRequest request,
            Authentication authentication) {
        String username = authentication != null ? authentication.getName() : "system";
        trustedLogSourceService.blockLogSource(request.getSourceIp(), username);
        return Result.success();
    }

    /**
     * 删除日志源
     */
    @PostMapping("/delete")
    public Result<Void> deleteLogSource(@RequestBody DeleteRequest request) {
        trustedLogSourceService.deleteLogSource(request.getSourceIp());
        return Result.success();
    }

    /**
     * 检查 IP 是否在白名单中
     */
    @PostMapping("/check-trusted")
    public Result<Boolean> checkTrusted(@RequestBody CheckRequest request) {
        boolean trusted = trustedLogSourceService.isTrusted(request.getSourceIp());
        return Result.success(trusted);
    }

    /**
     * 检查 IP 是否被拉黑
     */
    @PostMapping("/check-blocked")
    public Result<Boolean> checkBlocked(@RequestBody CheckRequest request) {
        boolean blocked = trustedLogSourceService.isBlocked(request.getSourceIp());
        return Result.success(blocked);
    }

    /**
     * 接收 Vector 发送的新 IP 通知
     * 这个接口由 Vector 的 HTTP sink 调用
     */
    @PostMapping("/notify-new-ip")
    public Result<Void> notifyNewIp(@RequestBody VectorNewIpNotification notification) {
        log.info("收到 Vector 新 IP 通知: {}", notification.getSourceIp());

        boolean isTrusted = trustedLogSourceService.handleVectorNotification(
                notification.getSourceIp(),
                notification.getHostname(),
                notification.getLogCount()
        );

        if (!isTrusted) {
            log.warn("新日志源 {} 需要审核", notification.getSourceIp());
        }

        return Result.success();
    }

    /**
     * 获取待审核的通知（用于前端轮询）
     */
    @PostMapping("/pending-notifications")
    public Result<List<NewLogSourceNotification>> getPendingNotifications() {
        return Result.success(trustedLogSourceService.getPendingNotifications());
    }

    // 内部请求类
    @lombok.Data
    static class StatusRequest {
        private String status;
    }

    @lombok.Data
    static class BlockRequest {
        private String sourceIp;
    }

    @lombok.Data
    static class DeleteRequest {
        private String sourceIp;
    }

    @lombok.Data
    static class CheckRequest {
        private String sourceIp;
    }
}
