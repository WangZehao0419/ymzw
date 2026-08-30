package com.ruoyi.equipment.config;

import com.ruoyi.equipment.tdengine.TdSensorDataMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * TDengine 库表结构初始化器(建库 + 建超级表,幂等)
 * <p>
 * 不用 @PostConstruct 而用 ApplicationReadyEvent:Mapper 代理由
 * MapperFactoryBean 在容器刷新后期才产出,其初始化时机晚于普通
 * @PostConstruct 阶段,ApplicationReadyEvent 保证全部 Bean 就绪后再执行 DDL;
 * 建表失败仅记日志,不阻断启动(TDengine 允许后置部署,业务侧查询已有降级)。
 * </p>
 *
 * @author smartartisan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TdSchemaInitializer {

    private final TdSensorDataMapper tdSensorDataMapper;

    /**
     * 启动完成后建库建表(DDL 带 IF NOT EXISTS,可重复执行)
     */
    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        try {
            tdSensorDataMapper.createDatabase();
            tdSensorDataMapper.createStable();
            log.info("[TDengine] 建库建超级表完成(MyBatis)");
        } catch (Exception e) {
            log.error("[TDengine] 建库建表失败(不阻断启动): {}", e.getMessage());
        }
    }
}
