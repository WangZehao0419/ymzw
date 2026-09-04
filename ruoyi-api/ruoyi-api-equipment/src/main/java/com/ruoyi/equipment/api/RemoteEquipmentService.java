package com.ruoyi.equipment.api;

import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import com.ruoyi.common.core.constant.SecurityConstants;
import com.ruoyi.common.core.constant.ServiceNameConstants;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.equipment.api.domain.EquipmentMetaDTO;
import com.ruoyi.equipment.api.domain.SensorMetaDTO;
import com.ruoyi.equipment.api.domain.SensorPointDTO;
import com.ruoyi.equipment.api.factory.RemoteEquipmentFallbackFactory;

/**
 * 设备服务
 *
 * @author smartartisan
 */
@FeignClient(contextId = "remoteEquipmentService", value = ServiceNameConstants.EQUIPMENT_SERVICE, fallbackFactory = RemoteEquipmentFallbackFactory.class)
public interface RemoteEquipmentService
{
    /**
     * 根据设备ID查询设备元数据
     *
     * @param equipmentId 设备ID
     * @param source 请求来源
     * @return 结果
     */
    @GetMapping("/inner/equipment/{equipmentId}")
    public R<EquipmentMetaDTO> getEquipmentMeta(@PathVariable("equipmentId") Integer equipmentId, @RequestHeader(SecurityConstants.FROM_SOURCE) String source);

    /**
     * 根据传感器ID列表批量查询传感器元数据
     *
     * @param sensorIds 传感器ID列表
     * @param source 请求来源
     * @return 结果
     */
    @PostMapping("/inner/sensor/meta/batch")
    public R<List<SensorMetaDTO>> listSensorMetaByIds(@RequestBody List<Integer> sensorIds, @RequestHeader(SecurityConstants.FROM_SOURCE) String source);

    /**
     * 查询全部传感器元数据列表（预测性维护全量取数入口）
     *
     * @param source 请求来源
     * @return 结果
     */
    @GetMapping("/inner/sensor/list")
    public R<List<SensorMetaDTO>> listAllSensors(@RequestHeader(SecurityConstants.FROM_SOURCE) String source);

    /**
     * 查询传感器最近 n 条历史数据（时间升序,ts 为 epoch millis）
     *
     * @param sensorCode 传感器编码
     * @param points 窗口条数（缺省 600,上限 2000 超过截断;服务端异常/不存在均降级返回空列表）
     * @param source 请求来源
     * @return 结果
     */
    @GetMapping("/inner/sensor/{sensorCode}/history")
    public R<List<SensorPointDTO>> getSensorHistory(@PathVariable("sensorCode") String sensorCode, @RequestParam("points") Integer points, @RequestHeader(SecurityConstants.FROM_SOURCE) String source);

    /**
     * 下发维护复位指令（工单完成联动，MQTT maintenance/{equipmentNo}）
     *
     * @param equipmentId 设备ID
     * @param source 请求来源
     * @return 结果
     */
    @PostMapping("/inner/maintenance/{equipmentId}/reset")
    public R<Void> resetDegradation(@PathVariable("equipmentId") Integer equipmentId, @RequestHeader(SecurityConstants.FROM_SOURCE) String source);
}
