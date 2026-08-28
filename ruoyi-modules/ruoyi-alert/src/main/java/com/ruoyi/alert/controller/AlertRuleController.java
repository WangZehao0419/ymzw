package com.ruoyi.alert.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.alert.entity.AlertRule;
import com.ruoyi.alert.service.RuleService;
import com.ruoyi.common.core.web.domain.AjaxResult;
import com.ruoyi.common.core.web.page.TableDataInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 告警规则管理接口
 *
 * @author smartartisan
 */
@RestController
@RequestMapping("/api/alert-rules")
@RequiredArgsConstructor
public class AlertRuleController {

    private final RuleService ruleService;

    /**
     * 分页查询规则
     */
    @GetMapping("/page")
    public TableDataInfo page(@RequestParam(defaultValue = "1") long page,
                                         @RequestParam(defaultValue = "10") long size) {
        Page<AlertRule> p = ruleService.page(new Page<>(page, size));
        return new TableDataInfo(p.getRecords(), p.getTotal());
    }

    /**
     * 查询单个规则
     */
    @GetMapping("/{id}")
    public AjaxResult detail(@PathVariable Long id) {
        AlertRule rule = ruleService.getById(id);
        return rule == null ? AjaxResult.error("规则不存在") : AjaxResult.success(rule);
    }

    /**
     * 新增规则
     */
    @PostMapping
    public AjaxResult save(@RequestBody AlertRule rule) {
        if (rule.getSensorCode() == null || rule.getSensorCode().isEmpty()) {
            return AjaxResult.error("传感器编号不能为空");
        }
        if (rule.getSustainPoints() == null || rule.getSustainPoints() < 1) {
            rule.setSustainPoints(1);
        }
        ruleService.save(rule);
        return AjaxResult.success();
    }

    /**
     * 更新规则
     */
    @PutMapping("/{id}")
    public AjaxResult update(@PathVariable Long id, @RequestBody AlertRule rule) {
        rule.setId(id);
        ruleService.updateById(rule);
        return AjaxResult.success();
    }

    /**
     * 删除规则
     */
    @DeleteMapping("/{id}")
    public AjaxResult delete(@PathVariable Long id) {
        ruleService.removeById(id);
        return AjaxResult.success();
    }
}
