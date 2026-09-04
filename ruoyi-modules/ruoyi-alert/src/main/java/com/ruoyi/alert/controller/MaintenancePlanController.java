package com.ruoyi.alert.controller;

import com.ruoyi.alert.entity.MaintenancePlan;
import com.ruoyi.alert.service.MaintenancePlanService;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.core.web.page.TableDataInfo;
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

/**
 * 维护计划接口
 * <p>
 * 计划的建/改/删与暂停/恢复均含状态与规则校验,统一走 MaintenancePlanService;
 * 分页查询也在 Service 内实现(与查询侧共用规则过滤口径),Controller 只做参数接收。
 * 失败场景 catch ServiceException 转为 R.fail 透传业务消息,成功统一 R 返回
 * (与 WorkOrderController 的 try-catch 口径一致,仅返回体用 R)。
 * </p>
 *
 * @author smartartisan
 */
@Slf4j
@RestController
@RequestMapping("/api/maintenance-plans")
@RequiredArgsConstructor
public class MaintenancePlanController {

    private final MaintenancePlanService maintenancePlanService;

    /**
     * 分页查询计划
     * <p>
     * 筛选参数均可选,仅在前端实际传值时才拼接条件;
     * keyword 为模糊检索,同时匹配计划编号/计划名称/设备名。
     * </p>
     */
    @GetMapping("/page")
    public TableDataInfo page(@RequestParam(defaultValue = "1") int page,
                              @RequestParam(defaultValue = "10") int size,
                              @RequestParam(required = false) String status,
                              @RequestParam(required = false) String repeatType,
                              @RequestParam(required = false) String keyword) {
        return maintenancePlanService.page(page, size, status, repeatType, keyword);
    }

    /**
     * 创建计划
     * <p>
     * 返回体携带落库后的完整计划(含 nextFireTime):
     * 前端创建后需要立即展示"下次触发时间",不必再发一次查询。
     * </p>
     */
    @PostMapping
    public R<MaintenancePlan> create(@RequestBody MaintenancePlan plan) {
        try {
            return R.ok(maintenancePlanService.create(plan));
        } catch (ServiceException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 修改计划(id 以路径为准,忽略请求体内的 id)
     */
    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody MaintenancePlan plan) {
        try {
            plan.setId(id);
            maintenancePlanService.update(plan);
            return R.ok();
        } catch (ServiceException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 暂停计划(调度不再到点建单)
     */
    @PostMapping("/{id}/pause")
    public R<Void> pause(@PathVariable Long id) {
        try {
            maintenancePlanService.pause(id);
            return R.ok();
        } catch (ServiceException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 恢复计划(next_fire_time 以当前时刻重算,不补暂停期错过的触发)
     */
    @PostMapping("/{id}/resume")
    public R<Void> resume(@PathVariable Long id) {
        try {
            maintenancePlanService.resume(id);
            return R.ok();
        } catch (ServiceException e) {
            return R.fail(e.getMessage());
        }
    }

    /**
     * 删除计划(已生成工单的计划拒删,可改为暂停)
     */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        try {
            maintenancePlanService.delete(id);
            return R.ok();
        } catch (ServiceException e) {
            return R.fail(e.getMessage());
        }
    }
}
