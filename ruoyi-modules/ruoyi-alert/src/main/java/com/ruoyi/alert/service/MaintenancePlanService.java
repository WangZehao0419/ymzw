package com.ruoyi.alert.service;

import com.ruoyi.alert.entity.MaintenancePlan;
import com.ruoyi.common.core.web.page.TableDataInfo;

/**
 * 维护计划服务
 * <p>
 * 计划的生命周期为人工建/改/删 + 暂停/恢复;next_fire_time 由后端在建改与
 * 恢复时预计算落库,调度任务只做"到点建单 + 推进"。创建/修改共用一套
 * 规则必填校验,保证两条入口落库的数据同等可信。
 * </p>
 *
 * @author smartartisan
 */
public interface MaintenancePlanService {

    /**
     * 创建维护计划
     * <p>
     * 校验规则必填项(ONCE 须 fireDate 且不得已过、MONTHLY 须 fireDay 1-31)后,
     * 生成计划编号 MP+yyyyMMddHHmmss+3位随机,以当前时刻预计算首触时间,
     * 计划落库即为启用状态。
     * </p>
     *
     * @param plan 计划信息(不含 id/planNo/nextFireTime/status)
     * @return 落库后的计划(含 id/planNo/nextFireTime,便于前端直接展示)
     */
    MaintenancePlan create(MaintenancePlan plan);

    /**
     * 修改维护计划
     * <p>
     * DONE 为终态拒改(一次性计划已触发完结,再改规则等于复活一条已结束的计划);
     * ENABLED 以当前时刻重算 next_fire_time;PAUSED 保持暂停且同步重算,
     * 保证库内触发点始终符合最新规则。
     * </p>
     *
     * @param plan 计划信息(id 必传,字段以请求体为准)
     * @return 修改后的计划
     */
    MaintenancePlan update(MaintenancePlan plan);

    /**
     * 暂停计划
     * <p>
     * 仅 ENABLED 可暂停:调度任务按状态过滤,暂停即不再到点建单;
     * 已暂停的重复暂停无意义,DONE 已终态。
     * </p>
     *
     * @param id 计划ID
     */
    void pause(Long id);

    /**
     * 恢复计划
     * <p>
     * 仅 PAUSED 可恢复;next_fire_time 以当前时刻重算而非沿用暂停前旧值,
     * 防止恢复瞬间对暂停期错过的触发点连续补建过期工单。
     * </p>
     *
     * @param id 计划ID
     */
    void resume(Long id);

    /**
     * 删除计划
     * <p>
     * 已生成过工单的计划拒删:工单的 plan_id 溯源链会断裂,历史凭证不可丢,
     * 停用应走暂停。
     * </p>
     *
     * @param id 计划ID
     */
    void delete(Long id);

    /**
     * 分页查询计划
     *
     * @param page       页码(从 1 起)
     * @param size        每页条数
     * @param status      状态精确过滤(ENABLED/PAUSED/DONE,空则不过滤)
     * @param repeatType  重复类型精确过滤(ONCE/DAILY/WEEKDAYS/MONTHLY/LEGAL_WORKDAY,空则不过滤)
     * @param keyword     关键字模糊匹配 计划编号/计划名称/设备名(空则不过滤)
     * @return 分页结果(按创建时间倒序)
     */
    TableDataInfo page(int page, int size, String status, String repeatType, String keyword);

    /**
     * 扫描并处理所有到点计划(定时任务入口,核心调度闭环)
     * <p>
     * 查询 ENABLED 且 next_fire_time 已到的计划,逐条"到点建单 + 推进":
     * 正常到点经工单服务建单(同日同计划幂等)后以原触发点为基准推进;
     * 陈旧触发点(早于当前 24 小时,停机/故障期错过)跳过建单只推进;
     * ONCE 触发后置 DONE 终态。建单与推进同事务,单条处理失败仅记日志
     * 不阻断整批,下一轮扫描自然重试。
     * </p>
     *
     * @return 本轮成功处理的计划条数(建单与纯推进均计入,单条异常跳过的不计)
     */
    int fireDuePlans();
}
