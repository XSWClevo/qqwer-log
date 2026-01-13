package cn.mw.loganalysis.vector.service;

import cn.mw.loganalysis.vector.entity.VectorPackage;
import cn.mw.loganalysis.vector.mapper.VectorPackageMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
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
        // 创建存储目录
        Path storageDir = Paths.get(storagePath, packageType, osType, arch);
        Files.createDirectories(storageDir);
        
        // 保存文件
        String fileName = String.format("%s-%s-%s-%s", packageType, version, osType, arch);
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
        LambdaUpdateWrapper<VectorPackage> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(VectorPackage::getPackageType, packageType)
                     .eq(VectorPackage::getOsType, osType)
                     .eq(VectorPackage::getArch, arch)
                     .set(VectorPackage::getIsLatest, false);
        packageMapper.update(null, updateWrapper);
        
        // 创建记录
        VectorPackage pkg = new VectorPackage();
        pkg.setPackageType(packageType);
        pkg.setVersion(version);
        pkg.setOsType(osType);
        pkg.setArch(arch);
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
                packageType, version, osType, arch, filePath.toAbsolutePath());
        
        return pkg;
    }
    
    /**
     * 获取最新版本
     */
    public VectorPackage getLatestPackage(String packageType, String osType, String arch) {
        LambdaQueryWrapper<VectorPackage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VectorPackage::getPackageType, packageType)
               .eq(VectorPackage::getOsType, osType)
               .eq(VectorPackage::getArch, arch)
               .eq(VectorPackage::getIsLatest, true);
        
        return packageMapper.selectOne(wrapper);
    }
    
    /**
     * 获取所有版本
     */
    public List<VectorPackage> getAllPackages(String packageType) {
        LambdaQueryWrapper<VectorPackage> wrapper = new LambdaQueryWrapper<>();
        if (packageType != null && !packageType.isEmpty()) {
            wrapper.eq(VectorPackage::getPackageType, packageType);
        }
        wrapper.orderByDesc(VectorPackage::getCreatedAt);
        
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
}
