package cn.mw.loganalysis.alert.service;

import cn.mw.loganalysis.alert.dto.AlertRuleDTO;
import cn.mw.loganalysis.alert.dto.AlertRuleQueryRequest;
import cn.mw.loganalysis.alert.dto.CreateAlertRuleRequest;
import cn.mw.loganalysis.alert.dto.UpdateAlertRuleRequest;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.Map;

/**
 * 告警规则服务接口。
 */
public interface AlertRuleService {

    IPage<AlertRuleDTO> queryRules(AlertRuleQueryRequest request);

    AlertRuleDTO getRuleById(Long id);

    AlertRuleDTO createRule(CreateAlertRuleRequest request);

    AlertRuleDTO updateRule(Long id, UpdateAlertRuleRequest request);

    void deleteRule(Long id);

    AlertRuleDTO toggleRuleStatus(Long id, Boolean enabled);

    AlertRuleDTO duplicateRule(Long id);

    Map<String, Object> testRule(Long id);
}
