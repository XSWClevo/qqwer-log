package cn.mw.loganalysis.alert.mapper;

import cn.mw.loganalysis.alert.entity.AlertNotification;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 告警通知 Mapper
 */
@Mapper
@DS("postgres")
public interface AlertNotificationMapper extends BaseMapper<AlertNotification> {
}
