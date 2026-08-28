package com.ruoyi.alert.service.impl;

import com.ruoyi.alert.repository.BaseRepository;
import com.ruoyi.alert.entity.AlertRule;
import com.ruoyi.alert.mapper.AlertRuleMapper;
import com.ruoyi.alert.service.RuleService;
import org.springframework.stereotype.Service;

/**
 * 告警规则服务实现
 *
 * @author smartartisan
 */
@Service
public class RuleServiceImpl extends BaseRepository<AlertRuleMapper, AlertRule> implements RuleService {
    @org.springframework.beans.factory.annotation.Autowired
    private AlertRuleMapper baseMapper;

    @Override
    public AlertRuleMapper getBaseMapper() {
        return baseMapper;
    }

}
