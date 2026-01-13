package cn.mw.loganalysis.config.mapper;

import cn.mw.loganalysis.config.entity.SystemConfig;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 系统配置 Mapper
 */
@Mapper
public interface SystemConfigMapper extends BaseMapper<SystemConfig> {

    /**
     * 根据配置类型查询所有配置
     */
    default List<SystemConfig> selectByConfigType(String configType) {
        LambdaQueryWrapper<SystemConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SystemConfig::getConfigType, configType)
               .orderByAsc(SystemConfig::getConfigKey);
        return selectList(wrapper);
    }

    /**
     * 根据配置类型和配置键查询
     */
    default SystemConfig selectByTypeAndKey(String configType, String configKey) {
        LambdaQueryWrapper<SystemConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SystemConfig::getConfigType, configType)
               .eq(SystemConfig::getConfigKey, configKey);
        return selectOne(wrapper);
    }
}
