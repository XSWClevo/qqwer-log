package cn.mw.loganalysis.vector.mapper;

import cn.mw.loganalysis.vector.entity.VectorMachine;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Vector机器管理 Mapper
 */
@Mapper
@DS("postgres")
public interface VectorMachineMapper extends BaseMapper<VectorMachine> {

    /**
     * 根据条件查询机器列表
     */
    default List<VectorMachine> selectByCondition(String name, String status, String hostname) {
        LambdaQueryWrapper<VectorMachine> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(name), VectorMachine::getName, name)
               .eq(StringUtils.hasText(status), VectorMachine::getStatus, status)
               .like(StringUtils.hasText(hostname), VectorMachine::getHostname, hostname)
               .orderByDesc(VectorMachine::getCreatedAt);
        return selectList(wrapper);
    }

    /**
     * 分页查询机器列表
     */
    default Page<VectorMachine> selectPageByCondition(Page<VectorMachine> page, String keyword, String status) {
        LambdaQueryWrapper<VectorMachine> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(StringUtils.hasText(keyword), w -> w
                .like(VectorMachine::getName, keyword)
                .or()
                .like(VectorMachine::getHostname, keyword)
                .or()
                .like(VectorMachine::getIpAddress, keyword))
               .eq(StringUtils.hasText(status), VectorMachine::getStatus, status)
               .orderByDesc(VectorMachine::getCreatedAt);
        return selectPage(page, wrapper);
    }

    /**
     * 根据状态查询机器列表
     */
    default List<VectorMachine> selectByStatus(String status) {
        LambdaQueryWrapper<VectorMachine> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VectorMachine::getStatus, status)
               .orderByAsc(VectorMachine::getName);
        return selectList(wrapper);
    }

    /**
     * 根据 hostname 查询机器
     */
    default VectorMachine selectByHostname(String hostname) {
        LambdaQueryWrapper<VectorMachine> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VectorMachine::getHostname, hostname);
        return selectOne(wrapper);
    }

    /**
     * 根据 IP 地址查询机器
     */
    default VectorMachine selectByIpAddress(String ipAddress) {
        if (!StringUtils.hasText(ipAddress)) {
            return null;
        }
        LambdaQueryWrapper<VectorMachine> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VectorMachine::getIpAddress, ipAddress);
        return selectOne(wrapper);
    }

    /**
     * 根据 Token 查询机器
     */
    default VectorMachine selectByToken(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        LambdaQueryWrapper<VectorMachine> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(VectorMachine::getAgentToken, token);
        return selectOne(wrapper);
    }
}
