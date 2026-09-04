package com.ruoyi.equipment.api.factory;

import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.equipment.api.RemoteEquipmentService;
import com.ruoyi.equipment.api.domain.EquipmentMetaDTO;
import com.ruoyi.equipment.api.domain.SensorMetaDTO;
import com.ruoyi.equipment.api.domain.SensorPointDTO;

/**
 * 设备服务降级处理
 *
 * @author smartartisan
 */
@Component
public class RemoteEquipmentFallbackFactory implements FallbackFactory<RemoteEquipmentService>
{
    private static final Logger log = LoggerFactory.getLogger(RemoteEquipmentFallbackFactory.class);

    @Override
    public RemoteEquipmentService create(Throwable throwable)
    {
        log.error("设备服务调用失败:{}", throwable.getMessage());
        return new RemoteEquipmentService()
        {
            @Override
            public R<EquipmentMetaDTO> getEquipmentMeta(Integer equipmentId, String source)
            {
                return R.fail("获取设备元数据失败:" + throwable.getMessage());
            }

            @Override
            public R<List<SensorMetaDTO>> listSensorMetaByIds(List<Integer> sensorIds, String source)
            {
                return R.fail("批量获取传感器元数据失败:" + throwable.getMessage());
            }

            @Override
            public R<List<SensorMetaDTO>> listAllSensors(String source)
            {
                // 全量列表是数据展示类查询:降级返回空列表而非 R.fail,调用方按空列表跳过本轮,不中断链路
                log.warn("获取传感器全量列表降级返回空列表");
                return R.ok(Collections.emptyList());
            }

            @Override
            public R<List<SensorPointDTO>> getSensorHistory(String sensorCode, Integer points, String source)
            {
                // 历史窗口是数据展示类查询:降级返回空列表而非 R.fail,调用方按空数据渲染,不中断链路
                log.warn("获取传感器历史窗口数据降级返回空列表, sensorCode={}, points={}", sensorCode, points);
                return R.ok(Collections.emptyList());
            }

            @Override
            public R<Void> resetDegradation(Integer equipmentId, String source)
            {
                // 与查询类降级不同:复位是指令下发,降级必须返回 fail 而非 ok,调用方据此走复位失败分支
                return R.fail("维护复位指令下发失败:" + throwable.getMessage());
            }
        };
    }
}
