package cn.mw.loganalysis.operationlog.converter;

import cn.mw.loganalysis.operationlog.dto.response.OperationLogDTO;
import cn.mw.loganalysis.operationlog.entity.UserOperationLog;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * 操作日志对象转换器
 *
 * @author Claude
 * @since 2026-01-07
 */
@Mapper(componentModel = "spring")
public interface OperationLogConverter {

    /**
     * Entity → DTO
     */
    OperationLogDTO toDTO(UserOperationLog entity);

    /**
     * Entity List → DTO List
     */
    List<OperationLogDTO> toDTOList(List<UserOperationLog> entities);
}
