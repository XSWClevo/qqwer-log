package cn.mw.loganalysis.vector.controller;

import cn.mw.loganalysis.common.response.Result;
import cn.mw.loganalysis.vector.entity.VectorCommand;
import cn.mw.loganalysis.vector.entity.VectorMachine;
import cn.mw.loganalysis.vector.entity.VectorPackage;
import cn.mw.loganalysis.vector.service.VectorCommandService;
import cn.mw.loganalysis.vector.service.VectorMachineService;
import cn.mw.loganalysis.vector.service.VectorPackageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Vector 安装包管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/vector/packages")
@RequiredArgsConstructor
public class VectorPackageController {
    
    private final VectorPackageService packageService;
    private final VectorCommandService commandService;
    private final VectorMachineService machineService;
    
    /**
     * 上传安装包
     */
    @PostMapping("/upload")
    public Result<VectorPackage> uploadPackage(
            @RequestParam("file") MultipartFile file,
            @RequestParam("packageType") String packageType,
            @RequestParam("version") String version,
            @RequestParam("osType") String osType,
            @RequestParam(value = "arch", defaultValue = "amd64") String arch,
            @RequestParam(value = "changelog", required = false) String changelog) {
        try {
            VectorPackage pkg = packageService.uploadPackage(
                    file, packageType, version, osType, arch, changelog, "system");
            return Result.success(pkg);
        } catch (Exception e) {
            log.error("上传安装包失败", e);
            return Result.error("上传失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取所有安装包
     */
    @GetMapping("/list")
    public Result<List<VectorPackage>> listPackages(
            @RequestParam(required = false) String packageType) {
        return Result.success(packageService.getAllPackages(packageType));
    }
    
    /**
     * 获取最新版本
     */
    @GetMapping("/latest")
    public Result<VectorPackage> getLatestPackage(
            @RequestParam String packageType,
            @RequestParam String osType,
            @RequestParam(defaultValue = "amd64") String arch) {
        VectorPackage pkg = packageService.getLatestPackage(packageType, osType, arch);
        if (pkg == null) {
            return Result.error("未找到对应的安装包");
        }
        return Result.success(pkg);
    }
    
    /**
     * 下载安装包
     */
    @GetMapping("/download/{packageId}")
    public ResponseEntity<Resource> downloadPackage(@PathVariable String packageId) {
        try {
            Path filePath = packageService.getPackageFilePath(packageId);
            if (filePath == null || !filePath.toFile().exists()) {
                return ResponseEntity.notFound().build();
            }
            
            Resource resource = new FileSystemResource(filePath);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                            "attachment; filename=\"" + filePath.getFileName() + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
        } catch (Exception e) {
            log.error("下载安装包失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 下载 Bundle 安装包（根据系统类型自动选择）
     * 用于一键安装脚本
     */
    @GetMapping("/download-bundle")
    public ResponseEntity<Resource> downloadBundle(
            @RequestParam(defaultValue = "linux") String os,
            @RequestParam(defaultValue = "amd64") String arch) {
        try {
            // 查找最新的 bundle 包
            VectorPackage pkg = packageService.getLatestPackage("vector-agent-bundle", os, arch);
            if (pkg == null) {
                log.warn("未找到 bundle 包: os={}, arch={}", os, arch);
                return ResponseEntity.notFound().build();
            }
            
            Path filePath = Path.of(pkg.getDownloadPath());
            if (!filePath.toFile().exists()) {
                log.error("Bundle 文件不存在: {}", filePath);
                return ResponseEntity.notFound().build();
            }
            
            Resource resource = new FileSystemResource(filePath);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, 
                            "attachment; filename=\"" + pkg.getFileName() + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
        } catch (Exception e) {
            log.error("下载 Bundle 失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 删除安装包
     */
    @DeleteMapping("/{packageId}")
    public Result<Void> deletePackage(@PathVariable String packageId) {
        try {
            packageService.deletePackage(packageId);
            return Result.success();
        } catch (Exception e) {
            log.error("删除安装包失败", e);
            return Result.error("删除失败: " + e.getMessage());
        }
    }
    
    /**
     * 发送升级命令
     */
    @PostMapping("/upgrade")
    public Result<VectorCommand> sendUpgradeCommand(
            @RequestBody Map<String, String> request) {
        String machineId = request.get("machineId");
        String packageType = request.get("packageType");  // vector-agent 或 vector
        String targetVersion = request.get("targetVersion");
        
        if (machineId == null || packageType == null) {
            return Result.error("参数不完整");
        }
        
        // 获取机器信息
        VectorMachine machine = machineService.getMachineById(machineId);
        if (machine == null) {
            return Result.error("机器不存在");
        }
        
        // 获取目标版本的安装包
        String osType = machine.getOsType() != null ? machine.getOsType() : "linux";
        String arch = "arm64";  // TODO: 从机器信息获取
        
        VectorPackage pkg;
        if (targetVersion != null && !targetVersion.isEmpty()) {
            // 指定版本
            pkg = packageService.getLatestPackage(packageType, osType, arch);
            // TODO: 按版本查询
        } else {
            // 最新版本
            pkg = packageService.getLatestPackage(packageType, osType, arch);
        }
        
        if (pkg == null) {
            return Result.error("未找到对应的安装包");
        }
        
        // 创建升级命令
        String commandType = "vector-agent".equals(packageType) ? "upgrade_agent" : "upgrade_vector";
        VectorCommand command = commandService.createUpgradeCommand(
                machineId, commandType, pkg.getVersion(), pkg.getId(), "system");
        
        return Result.success(command);
    }
}
