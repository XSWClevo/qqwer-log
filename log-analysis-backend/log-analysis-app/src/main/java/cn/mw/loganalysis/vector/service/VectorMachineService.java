package cn.mw.loganalysis.vector.service;

import cn.mw.loganalysis.common.constants.MachineConstants;
import cn.mw.loganalysis.common.enums.MachineStatus;
import cn.mw.loganalysis.common.exception.ResourceNotFoundException;
import cn.mw.loganalysis.vector.dto.AddMachineRequest;
import cn.mw.loganalysis.vector.dto.AgentRegisterRequest;
import cn.mw.loganalysis.vector.entity.VectorMachine;
import cn.mw.loganalysis.vector.mapper.VectorMachineMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Vector机器管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorMachineService extends ServiceImpl<VectorMachineMapper, VectorMachine> {

    private final VectorMachineMapper vectorMachineMapper;

    /**
     * 分页查询机器列表
     */
    public Page<VectorMachine> getMachinePage(int pageNum, int pageSize, String keyword, String status) {
        Page<VectorMachine> page = new Page<>(pageNum, pageSize);
        return vectorMachineMapper.selectPageByCondition(page, keyword, status);
    }

    /**
     * 根据ID查询机器
     */
    public VectorMachine getMachineById(String id) {
        return vectorMachineMapper.selectById(id);
    }

    /**
     * 根据状态查询机器列表
     */
    public List<VectorMachine> getMachinesByStatus(String status) {
        return vectorMachineMapper.selectByStatus(status);
    }

    /**
     * 根据 Token 查找机器
     */
    public VectorMachine findByToken(String token) {
        // 使用 Mapper 的 default 方法
        return vectorMachineMapper.selectByToken(token);
    }

    /**
     * 根据 Token 查找或创建机器（Agent 注册时使用）
     */
    @Transactional(rollbackFor = Exception.class)
    public VectorMachine findOrCreateByToken(String token, AgentRegisterRequest request) {
        // 先根据 token 查找
        VectorMachine machine = findByToken(token);
        
        if (machine != null) {
            // 更新机器信息
            machine.setHostname(request.getHostname());
            machine.setIpAddress(request.getIpAddress());
            machine.setAgentVersion(request.getAgentVersion());
            machine.setVectorVersion(request.getVectorVersion());
            machine.setOsType(request.getOsType());
            machine.setStatus(MachineStatus.ONLINE.getCode());
            machine.setLastHeartbeat(LocalDateTime.now());
            vectorMachineMapper.updateById(machine);
            log.info("Agent 重新注册: {}", machine.getName());
            return machine;
        }

        // 根据 hostname 查找（可能是换了 token 重新注册）
        machine = vectorMachineMapper.selectByHostname(request.getHostname());
        if (machine != null) {
            // 更新 token 和其他信息
            machine.setAgentToken(token);
            machine.setIpAddress(request.getIpAddress());
            machine.setAgentVersion(request.getAgentVersion());
            machine.setVectorVersion(request.getVectorVersion());
            machine.setOsType(request.getOsType());
            machine.setStatus(MachineStatus.ONLINE.getCode());
            machine.setLastHeartbeat(LocalDateTime.now());
            vectorMachineMapper.updateById(machine);
            log.info("Agent 更新 Token 重新注册: {}", machine.getName());
            return machine;
        }

        // 创建新机器
        machine = new VectorMachine();
        machine.setName(request.getHostname());
        machine.setHostname(request.getHostname());
        machine.setIpAddress(request.getIpAddress());
        machine.setAgentToken(token);
        machine.setAgentVersion(request.getAgentVersion());
        machine.setVectorVersion(request.getVectorVersion());
        machine.setOsType(request.getOsType() != null ? request.getOsType() : MachineConstants.DEFAULT_OS);
        machine.setManagementMethod(MachineConstants.DEFAULT_INSTALL_TYPE);
        machine.setStatus(MachineStatus.ONLINE.getCode());
        machine.setLastHeartbeat(LocalDateTime.now());
        machine.setCreatedBy(MachineConstants.DEFAULT_INSTALL_TYPE);
        
        vectorMachineMapper.insert(machine);
        log.info("Agent 首次注册: {}", machine.getName());
        return machine;
    }

    /**
     * 添加机器
     */
    @Transactional(rollbackFor = Exception.class)
    public VectorMachine addMachine(AddMachineRequest request, String userId) {
        VectorMachine machine = new VectorMachine();
        BeanUtils.copyProperties(request, machine);

        // 设置默认值
        if (machine.getOsType() == null) {
            machine.setOsType(MachineConstants.DEFAULT_OS);
        }
        if (machine.getManagementMethod() == null) {
            machine.setManagementMethod(MachineConstants.DEFAULT_MANAGEMENT_TOOL);
        }
        if (machine.getVectorInstallPath() == null) {
            machine.setVectorInstallPath(MachineConstants.DEFAULT_VECTOR_INSTALL_PATH);
        }
        if (machine.getVectorConfigPath() == null) {
            machine.setVectorConfigPath(MachineConstants.DEFAULT_VECTOR_CONFIG_PATH);
        }

        machine.setStatus(MachineStatus.OFFLINE.getCode());
        machine.setCreatedBy(userId);

        vectorMachineMapper.insert(machine);
        log.info("添加Vector机器成功: {}", machine.getName());
        return machine;
    }

    /**
     * 更新机器信息
     */
    @Transactional(rollbackFor = Exception.class)
    public VectorMachine updateMachine(String id, AddMachineRequest request) {
        VectorMachine machine = vectorMachineMapper.selectById(id);
        if (machine == null) {
            throw new ResourceNotFoundException("机器不存在: " + id);
        }

        BeanUtils.copyProperties(request, machine);
        vectorMachineMapper.updateById(machine);
        log.info("更新Vector机器成功: {}", machine.getName());
        return machine;
    }

    /**
     * 删除机器
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteMachine(String id) {
        VectorMachine machine = vectorMachineMapper.selectById(id);
        if (machine == null) {
            throw new ResourceNotFoundException("机器不存在: " + id);
        }

        vectorMachineMapper.deleteById(id);
        log.info("删除Vector机器成功: {}", machine.getName());
    }

    /**
     * 更新机器状态
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateMachineStatus(String id, String status) {
        VectorMachine machine = vectorMachineMapper.selectById(id);
        if (machine == null) {
            return;
        }

        machine.setStatus(status);
        machine.setLastHeartbeat(LocalDateTime.now());
        vectorMachineMapper.updateById(machine);
        log.debug("更新机器状态: {} -> {}", machine.getName(), status);
    }

    /**
     * 更新心跳时间
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateHeartbeat(String id) {
        VectorMachine machine = vectorMachineMapper.selectById(id);
        if (machine == null) {
            return;
        }

        machine.setLastHeartbeat(LocalDateTime.now());
        machine.setStatus(MachineStatus.ONLINE.getCode());
        vectorMachineMapper.updateById(machine);
    }
}
