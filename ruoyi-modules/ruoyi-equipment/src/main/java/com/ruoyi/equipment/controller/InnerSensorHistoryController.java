package com.ruoyi.equipment.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.security.annotation.InnerAuth;
import com.ruoyi.equipment.api.domain.SensorPointDTO;
import com.ruoyi.equipment.entity.EquipmentSensor;
import com.ruoyi.equipment.service.EquipmentSensorService;
import com.ruoyi.equipment.tdengine.TdSensorDataMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 传感器历史窗口内部接口 Controller（内部服务调用）
 * <p>
 * 供告警等模块经 OpenFeign 拉取单个传感器最近 n 个时序点(趋势窗口),
 * 端点由 @InnerAuth 保护,仅限服务间携带内部凭证的调用,不对网关外暴露。
 * TDengine 超级表 tag 只有 sensor_id,按 sensor_code 查询需先回 MySQL
 * equipment_sensor 表换主键,再查时序库。
 * </p>
 *
 * @author smartartisan
 */
@Slf4j
@RestController
@RequestMapping("/inner")
@RequiredArgsConstructor
public class InnerSensorHistoryController {

    /**
     * points 缺省时的默认窗口条数
     */
    private static final int DEFAULT_POINTS = 600;

    /**
     * points 上限(超过截断,防止一次拉取拖垮 TDengine 与网络)
     */
    private static final int MAX_POINTS = 2000;

    private final TdSensorDataMapper tdSensorDataMapper;
    private final EquipmentSensorService sensorService;

    /**
     * 查询传感器最近 n 条历史数据（内部服务调用，@InnerAuth 保护）
     * <p>
     * 返回时间升序的 ts(epoch millis)/val 数据点列表;points 缺省 600、上限 2000;
     * 编码不存在、n&lt;=0 或查询异常一律返回空列表,不让异常冒泡中断调用方链路。
     * </p>
     *
     * @param sensorCode 传感器编码（如 TH-001）
     * @param points     窗口条数（默认 600，上限 2000 超过截断）
     * @return 升序时序数据点列表
     */
    @InnerAuth
    @GetMapping("/sensor/{sensorCode}/history")
    public R<List<SensorPointDTO>> getSensorHistory(@PathVariable("sensorCode") String sensorCode,
                                                    @RequestParam(value = "points", required = false) Integer points) {
        int n = points == null ? DEFAULT_POINTS : points;
        if (n <= 0) {
            return R.ok(Collections.emptyList());
        }
        if (n > MAX_POINTS) {
            n = MAX_POINTS;
        }
        try {
            // TDengine 只存 sensor_id tag,先按编码回 MySQL 换主键(sensor_code 有唯一键)
            EquipmentSensor sensor = sensorService.lambdaQuery()
                    .eq(EquipmentSensor::getSensorCode, sensorCode)
                    .one();
            if (sensor == null) {
                return R.ok(Collections.emptyList());
            }
            // DESC 取最近 n 条(保证窗口是"最近"数据),再反转为时间升序便于直接绘制趋势
            List<SensorPointDTO> desc = tdSensorDataMapper.selectRecentWindow(sensor.getId(), n);
            List<SensorPointDTO> asc = new ArrayList<>(desc);
            Collections.reverse(asc);
            return R.ok(asc);
        } catch (Exception e) {
            // TDengine 不可用等异常降级返回空列表,不冒泡
            log.warn("[TDengine] 传感器历史窗口查询失败, sensorCode={}, points={}: {}",
                    sensorCode, n, e.getMessage());
            return R.ok(Collections.emptyList());
        }
    }
}
