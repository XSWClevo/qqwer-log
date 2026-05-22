package cn.mw.loganalysis.vector.controller;

import cn.mw.loganalysis.common.response.Result;
import cn.mw.loganalysis.vector.dto.*;
import cn.mw.loganalysis.vector.entity.VectorCommand;
import cn.mw.loganalysis.vector.entity.VectorDeployment;
import cn.mw.loganalysis.vector.entity.VectorMachine;
import cn.mw.loganalysis.vector.entity.VectorPackage;
import cn.mw.loganalysis.vector.service.VectorCommandService;
import cn.mw.loganalysis.vector.service.VectorDeploymentService;
import cn.mw.loganalysis.vector.service.VectorMachineService;
import cn.mw.loganalysis.vector.service.VectorPackageService;
import cn.mw.loganalysis.vector.service.ComponentStatusService;
import cn.mw.loganalysis.vector.service.MachineMetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Vector Agent API 控制器
 * 供 Agent 调用的接口
 */
@Slf4j
@RestController
@RequestMapping("/api/vector/agents")
@RequiredArgsConstructor
public class VectorAgentController {

    private final VectorMachineService machineService;
    private final VectorDeploymentService deploymentService;
    private final VectorCommandService commandService;
    private final VectorPackageService packageService;
    private final ComponentStatusService componentStatusService;
    private final MachineMetricsService machineMetricsService;

    @Value("${server.port:8080}")
    private int serverPort;

    /**
     * Agent 注册
     */
    @PostMapping("/register")
    public Result<AgentRegisterResponse> register(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Validated @RequestBody AgentRegisterRequest request) {
        
        // 从 Authorization header 提取 token
        String token = extractToken(authHeader);
        
        log.info("Agent 注册请求: hostname={}, ip={}", request.getHostname(), request.getIpAddress());
        
        // 查找或创建机器记录
        VectorMachine machine = machineService.findOrCreateByToken(token, request);
        
        AgentRegisterResponse response = new AgentRegisterResponse();
        response.setHostId(machine.getId());
        response.setToken(token);
        
        return Result.success(response);
    }

    /**
     * Agent 心跳
     */
    @PostMapping("/heartbeat")
    public Result<Void> heartbeat(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Validated @RequestBody AgentHeartbeatRequest request) {
        
        String token = extractToken(authHeader);
        
        // 根据 token 查找机器并更新心跳
        VectorMachine machine = machineService.findByToken(token);
        if (machine != null) {
            machineService.updateHeartbeat(machine.getId());
            if (request.getStatus() != null) {
                machineService.updateMachineStatus(machine.getId(), request.getStatus());
            }
        }
        
        return Result.success();
    }

    /**
     * Agent 拉取配置
     */
    @GetMapping("/config")
    public Result<AgentConfigResponse> fetchConfig(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "Host", required = false) String host,
            @RequestHeader(value = "X-Forwarded-Proto", required = false) String forwardedProto) {
        
        String token = extractToken(authHeader);
        
        // 根据 token 查找机器
        VectorMachine machine = machineService.findByToken(token);
        if (machine == null) {
            return Result.error("机器未注册");
        }
        
        // 查找待部署的配置
        VectorDeployment deployment = deploymentService.getPendingDeployment(machine.getId());
        if (deployment == null) {
            // 没有待部署的配置，返回空
            return Result.success(new AgentConfigResponse());
        }
        
        AgentConfigResponse response = new AgentConfigResponse();
        response.setDeploymentId(deployment.getId());
        response.setVersion(deployment.getConfigVersion());
        response.setDeployMode(deployment.getDeployMode());
        
        // 生成该机器所有已部署配置的合并 config-dir 结构
        // 这样部署新配置时不会覆盖已有的其他配置
        Map<String, String> configFiles = deploymentService.getMergedConfigDir(
                machine.getId(),
                buildServerUrl(host, forwardedProto)
        );
        if (configFiles != null && !configFiles.isEmpty()) {
            response.setConfigFiles(configFiles);
            log.info("Agent 拉取配置 (config-dir): machine={}, version={}, files={}", 
                    machine.getName(), deployment.getConfigVersion(), configFiles.size());
        } else {
            // 兼容旧模式
            response.setYamlContent(deployment.getConfigContent());
            log.info("Agent 拉取配置 (单文件): machine={}, version={}", 
                    machine.getName(), deployment.getConfigVersion());
        }
        
        return Result.success(response);
    }

    private String buildServerUrl(String host, String forwardedProto) {
        String protocol = (forwardedProto != null && !forwardedProto.isEmpty()) ? forwardedProto : "http";
        return protocol + "://" + (host != null ? host : "localhost:" + serverPort);
    }

    /**
     * Agent 上报配置部署状态
     */
    @PostMapping("/config/deploy-status")
    public Result<Void> reportDeployStatus(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Validated @RequestBody AgentDeployStatusRequest request) {
        
        log.info("Agent 上报部署状态: deploymentId={}, status={}", 
                request.getDeploymentId(), request.getStatus());
        
        if (request.getDeploymentId() != null && !request.getDeploymentId().isEmpty()) {
            deploymentService.updateDeploymentStatus(
                    request.getDeploymentId(), 
                    request.getStatus(), 
                    request.getErrorMessage()
            );
        }
        
        return Result.success();
    }

    /**
     * Agent 上报指标
     */
    @PostMapping("/metrics")
    public Result<Void> reportMetrics(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Validated @RequestBody AgentMetricsRequest request) {
        
        String token = extractToken(authHeader);
        VectorMachine machine = machineService.findByToken(token);
        
        if (machine != null) {

            // 更新组件状态缓存
            componentStatusService.updateComponentStatus(machine.getId(), request);
            
            // 记录指标历史
            machineMetricsService.recordMetrics(machine.getId(), request);
            
            log.debug("Agent 上报指标: machineId={}, cpu={}%, mem={}%, vectorRunning={}, components={}",
                    machine.getId(),
                    request.getCpuUsagePercent(), 
                    request.getMemoryUsagePercent(),
                    request.getVectorRunning(),
                    request.getComponentMetrics() != null ? request.getComponentMetrics().keySet() : "null");
        } else {
            log.warn("Agent 上报指标失败: 未找到机器, token={}", token);
        }
        
        return Result.success();
    }

    /**
     * 获取机器的组件状态（供前端可视化配置使用）
     */
    @GetMapping("/component-status/{machineId}")
    public Result<ComponentStatusResponse> getComponentStatus(@PathVariable String machineId) {
        ComponentStatusResponse response = componentStatusService.getComponentStatus(machineId);
        return Result.success(response);
    }

    /**
     * Agent 上报日志
     */
    @PostMapping("/logs")
    public Result<Void> reportLog(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Validated @RequestBody AgentLogRequest request) {
        
        log.info("[Agent Log] [{}] {}", request.getLevel(), request.getMessage());
        
        return Result.success();
    }

    /**
     * 获取安装脚本
     */
    @GetMapping(value = "/install-script", produces = MediaType.TEXT_PLAIN_VALUE)
    public String getInstallScript(
            @RequestParam String token,
            @RequestHeader(value = "Host", required = false) String host) {
        // 从请求头获取服务器地址
        String serverUrl = "http://" + (host != null ? host : "localhost:" + serverPort);

        try {
            // 从 classpath 读取脚本模板
            ClassPathResource resource =
                new ClassPathResource("scripts/install-agent.sh");
            String script = new String(resource.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);

            // 替换占位符
            return script
                .replace("{{TOKEN}}", token)
                .replace("{{SERVER_URL}}", serverUrl);
        } catch (Exception e) {
            log.error("读取安装脚本失败", e);
            throw new RuntimeException("无法生成安装脚本", e);
        }
    }

    /**
     * 下载安装包
     */
    @GetMapping("/download")
    public ResponseEntity<org.springframework.core.io.Resource> downloadBundle(
            @RequestParam(defaultValue = "linux") String os,
            @RequestParam(defaultValue = "arm64") String arch) {
        try {
            // 优先从包管理系统获取最新上传的包
            VectorPackage latestPkg = packageService.getLatestPackage("vector-agent-bundle", os, arch);
            if (latestPkg != null) {
                java.nio.file.Path pkgPath = java.nio.file.Paths.get(latestPkg.getDownloadPath());
                if (java.nio.file.Files.exists(pkgPath)) {
                    org.springframework.core.io.Resource resource = new org.springframework.core.io.UrlResource(pkgPath.toUri());
                    return ResponseEntity.ok()
                            .header("Content-Disposition", "attachment; filename=\"" + latestPkg.getFileName() + "\"")
                            .header("Content-Type", "application/gzip")
                            .body(resource);
                }
                log.warn("包管理记录存在但文件不存在: {}", latestPkg.getDownloadPath());
            }

            // 回退：尝试从 classpath 加载
            String filename = "vector-agent-bundle-" + os + ".tar.gz";
            org.springframework.core.io.Resource resource = new org.springframework.core.io.ClassPathResource("static/downloads/" + filename);
            
            if (!resource.exists()) {
                // 尝试从文件系统加载
                java.nio.file.Path filePath = java.nio.file.Paths.get("static/downloads", filename);
                if (java.nio.file.Files.exists(filePath)) {
                    resource = new org.springframework.core.io.UrlResource(filePath.toUri());
                } else {
                    log.warn("安装包不存在: {}", filename);
                    return ResponseEntity.notFound().build();
                }
            }
            
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .header("Content-Type", "application/gzip")
                    .body(resource);
        } catch (Exception e) {
            log.error("下载安装包失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Agent 拉取待执行命令
     */
    @GetMapping("/command")
    public Result<AgentCommandResponse> fetchCommand(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "Host", required = false) String host) {
        
        String token = extractToken(authHeader);
        
        VectorMachine machine = machineService.findByToken(token);
        if (machine == null) {
            return Result.error("机器未注册");
        }
        
        VectorCommand command = commandService.getPendingCommand(machine.getId());
        if (command == null) {
            return Result.success(new AgentCommandResponse());
        }
        
        AgentCommandResponse response = new AgentCommandResponse();
        response.setCommandId(command.getId());
        response.setCommandType(command.getCommandType());
        
        // 如果是升级命令，填充下载信息
        if (command.getCommandType().startsWith("upgrade_") && command.getPackageId() != null) {
            VectorPackage pkg = packageService.getPackageById(command.getPackageId());
            if (pkg != null) {
                response.setTargetVersion(pkg.getVersion());
                String serverUrl = "http://" + (host != null ? host : "localhost:" + serverPort);
                response.setDownloadUrl(serverUrl + "/api/vector/packages/download/" + pkg.getId());
                response.setChecksum(pkg.getChecksum());
                response.setFileSize(pkg.getFileSize());
            }
        }
        
        // 标记为执行中
        commandService.updateCommandStatus(command.getId(), "executing", null);
        
        log.info("Agent 拉取命令: machine={}, type={}", machine.getName(), command.getCommandType());
        
        return Result.success(response);
    }
    
    /**
     * Agent 上报命令执行状态
     */
    @PostMapping("/command/status")
    public Result<Void> reportCommandStatus(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Validated @RequestBody AgentCommandStatusRequest request) {
        
        log.info("Agent 上报命令状态: commandId={}, status={}", 
                request.getCommandId(), request.getStatus());
        
        commandService.updateCommandStatus(
                request.getCommandId(), 
                request.getStatus(), 
                request.getErrorMessage()
        );
        
        return Result.success();
    }
    
    /**
     * 发送控制命令（供前端调用）
     */
    @PostMapping("/send-command")
    public Result<VectorCommand> sendCommand(@Validated @RequestBody SendCommandRequest request) {
        // 验证命令类型
        String type = request.getCommandType();
        if (!type.matches("^(start|stop|restart|reload)_vector$")) {
            return Result.error("无效的命令类型: " + type);
        }
        
        // 验证机器存在
        VectorMachine machine = machineService.getMachineById(request.getMachineId());
        if (machine == null) {
            return Result.error("机器不存在");
        }
        
        // 创建命令
        VectorCommand command = commandService.createCommand(
                request.getMachineId(), 
                request.getCommandType(), 
                "system"
        );
        
        return Result.success(command);
    }
    
    /**
     * 获取命令历史（供前端调用）
     */
    @GetMapping("/commands/{machineId}")
    public Result<java.util.List<VectorCommand>> getCommandHistory(
            @PathVariable String machineId,
            @RequestParam(defaultValue = "10") int limit) {
        
        return Result.success(commandService.getRecentCommands(machineId, limit));
    }

    /**
     * 重新下发指定机器的全量配置（含内部 pipeline）
     */
    @PostMapping("/redeploy/{machineId}")
    public Result<VectorDeployment> redeployConfig(@PathVariable String machineId) {
        VectorDeployment deployment = deploymentService.redeployAllConfig(machineId, "system");
        return Result.success(deployment);
    }

    /**
     * 调试接口：查看机器的合并配置
     * 用于排查配置合并问题
     */
    @GetMapping("/debug/config-dir/{machineId}")
    public Result<Map<String, String>> debugConfigDir(@PathVariable String machineId) {
        Map<String, String> configFiles = deploymentService.getMergedConfigDir(machineId);
        log.info("调试 - 机器 {} 的配置文件: {}", machineId, configFiles.keySet());
        return Result.success(configFiles);
    }

    /**
     * 从 Authorization header 提取 token
     */
    private String extractToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return authHeader;
    }
}
