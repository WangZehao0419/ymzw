package com.ruoyi.alert.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.alert.entity.MaintenancePlan;
import com.ruoyi.alert.entity.WorkOrder;
import com.ruoyi.alert.mapper.MaintenancePlanMapper;
import com.ruoyi.alert.mapper.WorkOrderMapper;
import com.ruoyi.alert.plan.HolidayService;
import com.ruoyi.alert.plan.NextFireTimeCalculator;
import com.ruoyi.alert.service.MaintenancePlanService;
import com.ruoyi.alert.service.WorkOrderService;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.core.web.page.TableDataInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 维护计划服务实现
 *
 * @author smartartisan
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaintenancePlanServiceImpl implements MaintenancePlanService {

    /** 计划编号时间戳格式:MP + yyyyMMddHHmmss + 3位随机(与工单编号同口径) */
    private static final DateTimeFormatter PLAN_NO_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** 合法重复类型白名单:校验入口挡非法值,避免脏数据流入 Calculator 兜底抛错 */
    private static final Set<String> REPEAT_TYPES = Set.of("ONCE", "DAILY", "WEEKDAYS", "MONTHLY", "LEGAL_WORKDAY");

    private final MaintenancePlanMapper maintenancePlanMapper;

    // 删除校验需查计划名下工单数,复用既有工单 Mapper 不另起炉灶
    private final WorkOrderMapper workOrderMapper;

    // 到点触发时经工单服务建单(内含同日同计划幂等),复用既有封装不直写 work_order 表
    private final WorkOrderService workOrderService;

    // LEGAL_WORKDAY 规则的触发点计算依赖法定工作日判定(含调休补班)
    private final HolidayService holidayService;

    @Override
    public MaintenancePlan create(MaintenancePlan plan) {
        validatePlan(plan);
        plan.setPlanNo(generatePlanNo());
        // 首触时间预计算落库:调度任务只做"到点建单+推进",不在触发路径上做规则计算
        plan.setNextFireTime(NextFireTimeCalculator.firstFire(plan, LocalDateTime.now(), holidayService::isLegalWorkday));
        plan.setStatus("ENABLED");
        try {
            maintenancePlanMapper.insert(plan);
        } catch (DuplicateKeyException e) {
            // plan_no 唯一键冲突:秒级时间戳+3位随机的组合在并发下仍可能撞号,换号重试一次即可
            plan.setPlanNo(generatePlanNo());
            maintenancePlanMapper.insert(plan);
        }
        return plan;
    }

    @Override
    public MaintenancePlan update(MaintenancePlan plan) {
        MaintenancePlan db = requirePlan(plan.getId());
        // DONE 拒改:一次性计划触发完结即终态,再改规则等于复活一条已结束的计划,
        // 且其历史工单的溯源规则与库内不一致会误导审计
        if ("DONE".equals(db.getStatus())) {
            throw new ServiceException("已完成的计划不可修改");
        }
        validatePlan(plan);
        // status/planNo 以库内为准而非请求体:防前端篡改 status 绕过 DONE 拒改等
        // 状态约束,planNo 是唯一业务编号创建后不允许经 update 变更
        plan.setStatus(db.getStatus());
        plan.setPlanNo(db.getPlanNo());
        // 规则字段变了触发点就变了:ENABLED 以当前时刻重算;PAUSED 同步重算,
        // 恢复(resume)时会再算一遍——两处同口径,保证任何入口落库的
        // next_fire_time 都符合最新规则而非残留旧值
        plan.setNextFireTime(NextFireTimeCalculator.firstFire(plan, LocalDateTime.now(), holidayService::isLegalWorkday));
        maintenancePlanMapper.updateById(plan);
        return plan;
    }

    @Override
    public void pause(Long id) {
        MaintenancePlan plan = requirePlan(id);
        // 已暂停的重复暂停无意义,DONE 已终态不可再挂起,仅启用中的计划有暂停语义
        if (!"ENABLED".equals(plan.getStatus())) {
            throw new ServiceException("仅启用中的计划可暂停");
        }
        MaintenancePlan upd = new MaintenancePlan();
        upd.setId(id);
        upd.setStatus("PAUSED");
        maintenancePlanMapper.updateById(upd);
    }

    @Override
    public void resume(Long id) {
        MaintenancePlan plan = requirePlan(id);
        if (!"PAUSED".equals(plan.getStatus())) {
            throw new ServiceException("仅已暂停的计划可恢复");
        }
        MaintenancePlan upd = new MaintenancePlan();
        upd.setId(id);
        upd.setStatus("ENABLED");
        // 以当前时刻重算而非沿用暂停前的旧值:暂停期错过的触发点已无补做意义,
        // 沿用旧值会令恢复瞬间对历史时刻连续补建一串"过期"工单
        upd.setNextFireTime(NextFireTimeCalculator.firstFire(plan, LocalDateTime.now(), holidayService::isLegalWorkday));
        maintenancePlanMapper.updateById(upd);
    }

    @Override
    public void delete(Long id) {
        requirePlan(id);
        // 有工单拒删保凭证:预防维护工单经 related_id+order_type 溯源到计划,删计划
        // 会令溯源链断裂、历史维保记录失去上下文,停用应走暂停而非删除;
        // 须叠加 order_type=预防维护 收窄——related_id 对故障维修单指向 alert_event,
        // 裸按 related_id 查会把 id 碰撞的故障单误算进来造成误拒删
        Long orderCount = workOrderMapper.selectCount(new LambdaQueryWrapper<WorkOrder>()
                .eq(WorkOrder::getRelatedId, id)
                .eq(WorkOrder::getOrderType, "预防维护"));
        if (orderCount != null && orderCount > 0) {
            throw new ServiceException("该计划已生成工单，不能删除，可改为暂停");
        }
        maintenancePlanMapper.deleteById(id);
    }

    @Override
    public TableDataInfo page(int page, int size, String status, String repeatType, String keyword) {
        LambdaQueryWrapper<MaintenancePlan> wrapper = new LambdaQueryWrapper<MaintenancePlan>()
                .eq(StringUtils.hasText(status), MaintenancePlan::getStatus, status)
                .eq(StringUtils.hasText(repeatType), MaintenancePlan::getRepeatType, repeatType)
                // keyword 是 OR 语义组合条件,须用 and(...) 包成一组括号,
                // 否则 OR 会击穿前面的 eq 精确过滤条件
                .and(StringUtils.hasText(keyword), w -> w
                        .like(MaintenancePlan::getPlanNo, keyword)
                        .or().like(MaintenancePlan::getPlanName, keyword)
                        .or().like(MaintenancePlan::getEquipmentName, keyword))
                // 计划列表默认关心最新创建的计划,按创建时间倒序
                .orderByDesc(MaintenancePlan::getCreateTime);
        Page<MaintenancePlan> p = maintenancePlanMapper.selectPage(new Page<>(page, size), wrapper);
        return new TableDataInfo(p.getRecords(), p.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int fireDuePlans() {
        // 事务边界取舍:建单(createFromPlan)与推进(next_fire_time)必须同落同滚,否则建单
        // 成功、推进失败会留下"下一轮重复建单"窗口。整个 fireDuePlans 一个事务最简单
        // 可靠——单批到点计划数量少(计划总量本身有限),整批事务成本可忽略;processPlan
        // 单独标 @Transactional 会因同类内部调用不走代理而失效,TransactionTemplate 与
        // 自身代理注入皆为绕开它引入的复杂度,批量小的前提下不值得。另有工单侧
        // "同日同计划幂等"兜底,即便整批回滚后下轮重试,也不会重复建单
        LocalDateTime now = LocalDateTime.now();
        // 到点计划:仅 ENABLED 参与(DONE 的 next_fire_time 已置 NULL,NULL 与
        // <= now 比较恒为假,天然出局);按触发点升序,先到期先处理
        List<MaintenancePlan> duePlans = maintenancePlanMapper.selectList(new LambdaQueryWrapper<MaintenancePlan>()
                .eq(MaintenancePlan::getStatus, "ENABLED")
                .le(MaintenancePlan::getNextFireTime, now)
                .orderByAsc(MaintenancePlan::getNextFireTime));
        int processed = 0;
        for (MaintenancePlan plan : duePlans) {
            try {
                processPlan(plan, now);
                processed++;
            } catch (Exception e) {
                // 单条失败只记 error 跳过,不阻断整批:失败计划 next_fire_time 原地
                // 未动,下一轮整点扫描自然重试(建单侧同日幂等保证重试不重复)
                log.error("[PLAN] 计划触发处理失败,下轮重试: planNo={}", plan.getPlanNo(), e);
            }
        }
        return processed;
    }

    /**
     * 处理单条到点计划:按需建单并推进 next_fire_time
     * <p>
     * 陈旧触发点(早于当前 24 小时)是停机/故障期错过的触发,不补建"过期"
     * 工单只推进;正常到点建单后以原触发点(而非 now)为基准推进,调度延迟
     * 不改变周期节律。建单与推进同在 fireDuePlans 的事务内。
     * </p>
     */
    private void processPlan(MaintenancePlan plan, LocalDateTime now) {
        // ---- 陈旧触发点:早于当前超过 24 小时,是停机/故障恢复后的残留 ----
        // 对历史时刻补建工单已无维保意义(工单是"当下处理"的动作),只推进不建单
        if (plan.getNextFireTime().isBefore(now.minusHours(24))) {
            if ("ONCE".equals(repeatTypeOf(plan))) {
                // 陈旧 ONCE:触发点早已过去且从未处理,直接置 DONE 收口——Calculator
                // 对 ONCE 恒返回固定触发点,不收口会让恢复后的每轮扫描都再次命中同一
                // 陈旧点反复空转,补建过期工单同样无意义
                maintenancePlanMapper.update(null, new LambdaUpdateWrapper<MaintenancePlan>()
                        .eq(MaintenancePlan::getId, plan.getId())
                        .set(MaintenancePlan::getStatus, "DONE")
                        .set(MaintenancePlan::getNextFireTime, null)
                        .set(MaintenancePlan::getUpdateTime, LocalDateTime.now()));
                log.warn("[PLAN] 跳过陈旧触发点,一次性计划直接完结: planNo={}, 触发点={}",
                        plan.getPlanNo(), plan.getNextFireTime());
                return;
            }
            // 以当前时刻重算:陈旧点对应的周期早已错过,从 now 起找最近的未来触发
            // 点,避免下一轮仍命中陈旧点
            LocalDateTime next = NextFireTimeCalculator.next(plan, now, holidayService::isLegalWorkday);
            maintenancePlanMapper.update(null, new LambdaUpdateWrapper<MaintenancePlan>()
                    .eq(MaintenancePlan::getId, plan.getId())
                    .set(MaintenancePlan::getNextFireTime, next)
                    .set(MaintenancePlan::getUpdateTime, LocalDateTime.now()));
            log.warn("[PLAN] 跳过陈旧触发点未建单: planNo={}, 原触发点={}, 新触发点={}",
                    plan.getPlanNo(), plan.getNextFireTime(), next);
            return;
        }

        // ---- 正常到点:同日已有该计划工单时返回 null(跳过建单但仍推进) ----
        // 这是建单与推进非原子场景下的幂等兜底,防重复建单
        WorkOrder order = workOrderService.createFromPlan(plan);
        boolean once = "ONCE".equals(repeatTypeOf(plan));
        // 推进基准取原触发点而非 now:调度延迟(任务积压/恢复晚)不应顺延后续节律,
        // 否则每次延迟都把整个周期往后推;ONCE 无"下一次",next 置 null
        LocalDateTime next = once ? null
                : NextFireTimeCalculator.next(plan, plan.getNextFireTime(), holidayService::isLegalWorkday);
        LambdaUpdateWrapper<MaintenancePlan> uw = new LambdaUpdateWrapper<MaintenancePlan>()
                .eq(MaintenancePlan::getId, plan.getId())
                .set(MaintenancePlan::getLastFireTime, now)
                .set(MaintenancePlan::getNextFireTime, next);
        if (once) {
            // ONCE 触发即完结:DONE 终态 + next_fire_time 置 NULL(经 wrapper 显式
            // set null,updateById 非空更新清不掉),此后不再被扫描命中
            uw.set(MaintenancePlan::getStatus, "DONE");
        }
        // wrapper 路径无实体可走字段自动填充,update_time 显式落(与 PredictTask 同口径)
        uw.set(MaintenancePlan::getUpdateTime, LocalDateTime.now());
        maintenancePlanMapper.update(null, uw);
        log.info("[PLAN] 计划触发: planNo={}, 工单={}, 下次触发={}", plan.getPlanNo(),
                order != null ? order.getOrderNo() : "同日已有工单,跳过建单",
                next != null ? next : "已完结(DONE)");
    }

    /**
     * repeatType 归一(trim+大写),与 NextFireTimeCalculator 的归一口径一致:
     * 防手工改库的大小写/空格漂移令 ONCE 判定失效
     */
    private String repeatTypeOf(MaintenancePlan plan) {
        return plan.getRepeatType() == null ? "" : plan.getRepeatType().trim().toUpperCase();
    }

    /**
     * 创建/修改共用的规则必填校验
     * <p>
     * 两条入口共用一套校验,保证落库数据同等可信;repeatType 先归一(trim+大写)
     * 再比对,与 Calculator 的归一口径一致,挡掉手工数据的大小写/空格漂移。
     * </p>
     */
    private void validatePlan(MaintenancePlan plan) {
        if (!StringUtils.hasText(plan.getPlanName())) {
            throw new ServiceException("计划名称不能为空");
        }
        if (plan.getEquipmentId() == null) {
            throw new ServiceException("请选择维保设备");
        }
        if (!StringUtils.hasText(plan.getMaintenanceType())) {
            throw new ServiceException("请选择保养类型");
        }
        String repeatType = plan.getRepeatType() == null ? "" : plan.getRepeatType().trim().toUpperCase();
        if (!REPEAT_TYPES.contains(repeatType)) {
            throw new ServiceException("不支持的重复类型: " + plan.getRepeatType());
        }
        // 归一化回写:库内存规范大写值,Calculator 与列表过滤都按大写口径工作
        plan.setRepeatType(repeatType);
        if (plan.getFireTime() == null) {
            throw new ServiceException("请设置触发时刻");
        }
        if ("ONCE".equals(repeatType)) {
            if (plan.getFireDate() == null) {
                throw new ServiceException("一次性计划必须指定触发日期");
            }
            // 已过的触发时刻直接拒绝:Calculator 对 ONCE 恒返回固定时刻不做推迟,
            // 放行已过时刻会让调度立刻补建一张"过期"工单
            if (LocalDateTime.of(plan.getFireDate(), plan.getFireTime()).isBefore(LocalDateTime.now())) {
                throw new ServiceException("触发日期已过");
            }
        }
        if ("MONTHLY".equals(repeatType)
                && (plan.getFireDay() == null || plan.getFireDay() < 1 || plan.getFireDay() > 31)) {
            throw new ServiceException("每月触发日须为 1-31 的整数");
        }
    }

    /**
     * 生成计划编号:MP + yyyyMMddHHmmss + 3位随机数字(照 generateOrderNo 风格)
     */
    private String generatePlanNo() {
        return "MP" + LocalDateTime.now().format(PLAN_NO_TS)
                + String.format("%03d", ThreadLocalRandom.current().nextInt(1000));
    }

    /**
     * 加载计划,不存在时统一抛 ServiceException 由 Controller 转失败响应
     */
    private MaintenancePlan requirePlan(Long id) {
        MaintenancePlan plan = maintenancePlanMapper.selectById(id);
        if (plan == null) {
            throw new ServiceException("计划不存在");
        }
        return plan;
    }
}
