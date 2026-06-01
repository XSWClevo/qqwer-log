package cn.mw.loganalysis.vector.service;

import cn.mw.loganalysis.vector.entity.VectorPackage;
import cn.mw.loganalysis.vector.mapper.VectorPackageMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorPackageService {
    
    private final VectorPackageMapper packageMapper;
    
    @Value("${vector.packages.storage-path:./packages}")
    private String storagePath;
    
    /**
     * 上传安装包
     */
    public VectorPackage uploadPackage(MultipartFile file, String packageType, String version, 
                                        String osType, String arch, String changelog, String uploadedBy) throws IOException {
        String normalizedOsType = normalizeOsType(osType);
        String normalizedArch = normalizeArch(arch);

        // 创建存储目录
        Path storageDir = Paths.get(storagePath, packageType, normalizedOsType, normalizedArch);
        Files.createDirectories(storageDir);
        
        // 保存文件
        String fileName = String.format("%s-%s-%s-%s", packageType, version, normalizedOsType, normalizedArch);
        if (file.getOriginalFilename() != null && file.getOriginalFilename().contains(".")) {
            fileName += file.getOriginalFilename().substring(file.getOriginalFilename().lastIndexOf("."));
        }
        
        Path filePath = storageDir.resolve(fileName);
        
        // 使用 InputStream 方式保存文件（避免 transferTo 的路径问题）
        try (var inputStream = file.getInputStream();
             var outputStream = Files.newOutputStream(filePath)) {
            inputStream.transferTo(outputStream);
        }
        
        // 计算校验和
        String checksum = calculateSHA256(filePath);
        
        // 将同类型的其他版本设为非最新
        UpdateWrapper<VectorPackage> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("package_type", packageType)
                     .in("os_type", getOsTypeAliases(normalizedOsType))
                     .in("arch", getArchAliases(normalizedArch))
                     .set("is_latest", false);
        packageMapper.update(null, updateWrapper);
        
        // 创建记录
        VectorPackage pkg = new VectorPackage();
        pkg.setPackageType(packageType);
        pkg.setVersion(version);
        pkg.setOsType(normalizedOsType);
        pkg.setArch(normalizedArch);
        pkg.setFileName(fileName);
        pkg.setFileSize(file.getSize());
        pkg.setChecksum(checksum);
        pkg.setDownloadPath(filePath.toAbsolutePath().toString());
        pkg.setChangelog(changelog);
        pkg.setIsLatest(true);
        pkg.setCreatedAt(LocalDateTime.now());
        pkg.setUploadedBy(uploadedBy);
        
        packageMapper.insert(pkg);
        log.info("上传安装包: type={}, version={}, os={}, arch={}, path={}", 
                packageType, version, normalizedOsType, normalizedArch, filePath.toAbsolutePath());
        
        return pkg;
    }
    
    /**
     * 获取最新版本
     */
    public VectorPackage getLatestPackage(String packageType, String osType, String arch) {
        String normalizedOsType = normalizeOsType(osType);
        String normalizedArch = normalizeArch(arch);

        QueryWrapper<VectorPackage> wrapper = new QueryWrapper<>();
        wrapper.eq("package_type", packageType)
               .in("os_type", getOsTypeAliases(normalizedOsType))
               .in("arch", getArchAliases(normalizedArch))
               .eq("is_latest", true)
               .orderByDesc("created_at");

        List<VectorPackage> packages = packageMapper.selectList(wrapper);
        if (CollectionUtils.isEmpty(packages)) {
            return null;
        }

        return packages.get(0);
    }
    
    /**
     * 获取所有版本
     */
    public List<VectorPackage> getAllPackages(String packageType) {
        QueryWrapper<VectorPackage> wrapper = new QueryWrapper<>();
        if (StringUtils.isNotBlank(packageType)) {
            wrapper.eq("package_type", packageType);
        }
        wrapper.orderByDesc("created_at");
        
        return packageMapper.selectList(wrapper);
    }
    
    /**
     * 根据ID获取安装包
     */
    public VectorPackage getPackageById(String packageId) {
        return packageMapper.selectById(packageId);
    }
    
    /**
     * 获取包文件路径
     */
    public Path getPackageFilePath(String packageId) {
        VectorPackage pkg = packageMapper.selectById(packageId);
        if (pkg != null) {
            return Paths.get(pkg.getDownloadPath());
        }
        return null;
    }
    
    /**
     * 删除安装包
     */
    public void deletePackage(String packageId) throws IOException {
        VectorPackage pkg = packageMapper.selectById(packageId);
        if (pkg != null) {
            // 删除文件
            Path filePath = Paths.get(pkg.getDownloadPath());
            Files.deleteIfExists(filePath);
            
            // 删除记录
            packageMapper.deleteById(packageId);
            log.info("删除安装包: id={}", packageId);
        }
    }
    
    /**
     * 计算文件SHA256
     */
    private String calculateSHA256(Path filePath) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] fileBytes = Files.readAllBytes(filePath);
            byte[] hashBytes = digest.digest(fileBytes);
            
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IOException("计算校验和失败", e);
        }
    }

    String normalizeOsType(String osType) {
        String normalized = StringUtils.trimToEmpty(StringUtils.lowerCase(osType));
        if (StringUtils.isBlank(normalized)) {
            return "linux";
        }
        if (StringUtils.equalsAny(normalized, "macos", "mac", "osx")) {
            return "darwin";
        }
        return normalized;
    }

    String normalizeArch(String arch) {
        String normalized = StringUtils.trimToEmpty(StringUtils.lowerCase(arch));
        if (StringUtils.isBlank(normalized)) {
            return "amd64";
        }
        if (StringUtils.equalsAny(normalized, "x86_64", "x64")) {
            return "amd64";
        }
        if (StringUtils.equals(normalized, "aarch64")) {
            return "arm64";
        }
        return normalized;
    }

    public String defaultArchForOs(String osType) {
        String normalizedOsType = normalizeOsType(osType);
        if (StringUtils.equals(normalizedOsType, "darwin")) {
            return "arm64";
        }
        return "amd64";
    }

    private List<String> getOsTypeAliases(String osType) {
        List<String> aliases = new ArrayList<>();
        aliases.add(osType);
        if (StringUtils.equals(osType, "darwin")) {
            aliases.add("macos");
            aliases.add("mac");
            aliases.add("osx");
        }
        return aliases;
    }

    private List<String> getArchAliases(String arch) {
        List<String> aliases = new ArrayList<>();
        aliases.add(arch);
        if (StringUtils.equals(arch, "amd64")) {
            aliases.add("x86_64");
            aliases.add("x64");
        }
        if (StringUtils.equals(arch, "arm64")) {
            aliases.add("aarch64");
        }
        return aliases;
    }
}
