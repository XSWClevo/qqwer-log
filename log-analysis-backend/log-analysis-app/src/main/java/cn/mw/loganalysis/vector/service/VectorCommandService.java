package cn.mw.loganalysis.vector.service;

import cn.mw.loganalysis.common.enums.CommandStatus;
import cn.mw.loganalysis.vector.entity.VectorCommand;
import cn.mw.loganalysis.vector.mapper.VectorCommandMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class VectorCommandService {
    
    private final VectorCommandMapper commandMapper;
    
    /**
     * 创建命令
     */
    public VectorCommand createCommand(String machineId, String commandType, String createdBy) {
        VectorCommand command = new VectorCommand();
        command.setMachineId(machineId);
        command.setCommandType(commandType);
        command.setStatus(CommandStatus.PENDING.getCode());
        command.setCreatedAt(LocalDateTime.now());
        command.setCreatedBy(createdBy);

        commandMapper.insert(command);
        log.info("创建命令: machineId={}, type={}", machineId, commandType);

        return command;
    }

    /**
     * 创建升级命令
     */
    public VectorCommand createUpgradeCommand(String machineId, String commandType,
                                               String targetVersion, String packageId, String createdBy) {
        VectorCommand command = new VectorCommand();
        command.setMachineId(machineId);
        command.setCommandType(commandType);
        command.setTargetVersion(targetVersion);
        command.setPackageId(packageId);
        command.setStatus(CommandStatus.PENDING.getCode());
        command.setCreatedAt(LocalDateTime.now());
        command.setCreatedBy(createdBy);

        commandMapper.insert(command);
        log.info("创建升级命令: machineId={}, type={}, version={}", machineId, commandType, targetVersion);

        return command;
    }
    
    /**
     * 获取待执行的命令
     */
    public VectorCommand getPendingCommand(String machineId) {
        // 使用 Mapper 的 default 方法
        return commandMapper.selectPendingCommand(machineId, CommandStatus.PENDING.getCode());
    }
    
    /**
     * 根据ID获取命令
     */
    public VectorCommand getCommandById(String commandId) {
        return commandMapper.selectById(commandId);
    }
    
    /**
     * 更新命令状态
     */
    public void updateCommandStatus(String commandId, String status, String errorMessage) {
        VectorCommand command = commandMapper.selectById(commandId);
        if (command != null) {
            command.setStatus(status);
            command.setErrorMessage(errorMessage);

            if (CommandStatus.EXECUTING.getCode().equals(status)) {
                command.setExecutedAt(LocalDateTime.now());
            } else if (CommandStatus.SUCCESS.getCode().equals(status) ||
                       CommandStatus.FAILED.getCode().equals(status)) {
                command.setCompletedAt(LocalDateTime.now());
            }

            commandMapper.updateById(command);
            log.info("更新命令状态: id={}, status={}", commandId, status);
        }
    }
    
    /**
     * 获取机器的命令历史
     */
    public Page<VectorCommand> getCommandHistory(String machineId, int pageNum, int pageSize) {
        Page<VectorCommand> page = new Page<>(pageNum, pageSize);
        // 使用 Mapper 的 default 方法
        return commandMapper.selectPageByMachineId(page, machineId);
    }

    /**
     * 获取最近的命令列表
     */
    public List<VectorCommand> getRecentCommands(String machineId, int limit) {
        // 使用 Mapper 的 default 方法
        return commandMapper.selectRecentCommands(machineId, limit);
    }
}
