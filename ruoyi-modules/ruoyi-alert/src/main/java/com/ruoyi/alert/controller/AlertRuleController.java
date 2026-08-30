package com.ruoyi.alert.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.alert.entity.AlertRule;
import com.ruoyi.alert.service.RuleService;
import com.ruoyi.common.core.constant.SecurityConstants;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.core.web.domain.AjaxResult;
import com.ruoyi.common.core.web.page.TableDataInfo;
import com.ruoyi.equipment.api.RemoteEquipmentService;
import com.ruoyi.equipment.api.domain.SensorMetaDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 告警规则管理接口
 *
 * @author smartartisan
 */
@Slf4j
@RestController
@RequestMapping("/api/alert-rules")
@RequiredArgsConstructor
public class AlertRuleController {

    private final RuleService ruleService;

    // 传感器元数据经 Feign 查设备服务,不再直连 equipment_sensor 跨模块表
    private final RemoteEquipmentService remoteEquipmentService;

    /**
     * 分页查询规则
     * <p>
     * 规则表只存传感器ID,列表展示用的传感器名称经 Feign 查设备服务回填
     * (当前页 id 去重后一次批量查询,不做逐行调用)。
     * </p>
     */
    @GetMapping("/page")
    public TableDataInfo page(@RequestParam(defaultValue = "1") long page,
                                         @RequestParam(defaultValue = "10") long size) {
        Page<AlertRule> p = ruleService.page(new Page<>(page, size));
        fillSensorName(p.getRecords());
        return new TableDataInfo(p.getRecords(), p.getTotal());
    }

    /**
     * 按当前页规则的 sensorId 批量回填传感器名称
     * <p>
     * Feign 失败或传感器不存在时名称留空,列表照常返回,不受设备服务波动影响。
     * </p>
     */
    private void fillSensorName(List<AlertRule> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Integer> sensorIds = records.stream()
                .map(AlertRule::getSensorId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (sensorIds.isEmpty()) {
            return;
        }
        try {
            R<List<SensorMetaDTO>> result = remoteEquipmentService.listSensorMetaByIds(sensorIds, SecurityConstants.INNER);
            if (result == null || R.FAIL == result.getCode()
                    || result.getData() == null || result.getData().isEmpty()) {
                return;
            }
            Map<Integer, String> nameById = result.getData().stream()
                    .collect(Collectors.toMap(SensorMetaDTO::getId,
                            SensorMetaDTO::getSensorName, (a, b) -> a));
            records.forEach(r -> r.setSensorName(nameById.get(r.getSensorId())));
        } catch (Exception e) {
            // 降级:名称回填失败仅影响展示,不影响列表返回
            log.warn("[Rule] 传感器名称回填失败(列表仍返回): {}", e.getMessage());
        }
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
        AjaxResult check = checkAndFillSensorCode(rule);
        if (check != null) {
            return check;
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
        AjaxResult check = checkAndFillSensorCode(rule);
        if (check != null) {
            return check;
        }
        rule.setId(id);
        ruleService.updateById(rule);
        return AjaxResult.success();
    }

    /**
     * 按提交的 sensorId 经 Feign 校验传感器并回填 sensorCode
     * <p>
     * 提交体只携带 sensorId(主键标识),sensorCode 仅作展示兼容由设备服务查询回填,
     * 避免 alert 模块直连 equipment_sensor 跨模块表。
     * </p>
     *
     * @return 校验失败时返回错误响应;通过时返回 null
     */
    private AjaxResult checkAndFillSensorCode(AlertRule rule) {
        if (rule.getSensorId() == null) {
            return AjaxResult.error("请选择传感器");
        }
        try {
            R<List<SensorMetaDTO>> result = remoteEquipmentService.listSensorMetaByIds(
                    List.of(rule.getSensorId()), SecurityConstants.INNER);
            if (result == null || R.FAIL == result.getCode()
                    || result.getData() == null || result.getData().isEmpty()) {
                return AjaxResult.error("传感器不存在或设备服务不可用");
            }
            rule.setSensorCode(result.getData().get(0).getSensorCode());
            return null;
        } catch (Exception e) {
            // Feign 异常与失败响应同口径处理,避免把堆栈直接抛给前端
            log.warn("[Rule] 传感器校验失败: sensorId={}, error={}", rule.getSensorId(), e.getMessage());
            return AjaxResult.error("传感器不存在或设备服务不可用");
        }
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
