package cn.mw.loganalysis.vector.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Vector 安装包版本管理
 */
@Data
@TableName("vector_packages")
public class VectorPackage {
    
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    
    /**
     * 包类型: vector-agent, vector
     */
    private String packageType;
    
    /**
     * 版本号
     */
    private String version;
    
    /**
     * 操作系统: linux, darwin
     */
    private String osType;
    
    /**
     * CPU架构: amd64, arm64
     */
    private String arch;
    
    /**
     * 文件名
     */
    private String fileName;
    
    /**
     * 文件大小(字节)
     */
    private Long fileSize;
    
    /**
     * 文件SHA256校验和
     */
    private String checksum;
    
    /**
     * 下载路径
     */
    private String downloadPath;
    
    /**
     * 更新日志
     */
    private String changelog;
    
    /**
     * 是否为最新版本
     */
    private Boolean isLatest;
    
    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    
    /**
     * 上传人
     */
    private String uploadedBy;
}
