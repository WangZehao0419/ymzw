package com.ruoyi.equipment.controller;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.security.annotation.InnerAuth;
import com.ruoyi.equipment.entity.Equipment;
import com.ruoyi.equipment.service.EquipmentService;
import lombok.RequiredArgsConstructor;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * 维护复位内部接口 Controller（内部服务调用）
 * <p>
 * 供告警模块维保工单完成联动调用:向 MQTT 发布维护复位指令,
 * 模拟器 simulator-predict.py 已订阅 maintenance/# 并按 action=reset
 * 清零该设备全部传感器退化。
 * 端点由 @InnerAuth 保护,仅限服务间携带内部凭证的调用,
 * 不对网关外暴露。
 * </p>
 *
 * @author smartartisan
 */
@RestController
@RequestMapping("/inner")
@RequiredArgsConstructor
public class InnerMaintenanceController {

    private final EquipmentService equipmentService;
    private final MqttClient mqttClient;

    /**
     * 下发维护复位指令（内部服务调用，@InnerAuth 保护）
     * <p>
     * 以设备编码为粒度复位:主题 maintenance/{equipmentNo} 对齐模拟器的订阅规则,
     * QoS 1 保证指令至少送达一次,避免复位丢失导致退化残留。
     * </p>
     *
     * @param equipmentId 设备ID
     * @return 结果
     */
    @InnerAuth
    @PostMapping("/maintenance/{equipmentId}/reset")
    public R<Void> resetDegradation(@PathVariable Integer equipmentId) {
        Equipment equipment = equipmentService.getById(equipmentId);
        if (equipment == null) {
            return R.fail("设备不存在");
        }
        // 模拟器按设备编码路由复位,主题须用 equipmentNo 而非数据库主键
        String equipmentNo = equipment.getEquipmentNo();
        try {
            if (!mqttClient.isConnected()) {
                // Broker 不可达时 SmartLifecycle 仅告警不阻塞启动,客户端可能处于未连接态,须显式判失败走复位失败分支
                return R.fail("维护复位指令发布失败: MQTT 客户端未连接");
            }
            // payload 固定常量,无需引入 JSON 库
            mqttClient.publish("maintenance/" + equipmentNo, "{\"action\":\"reset\"}".getBytes(StandardCharsets.UTF_8), 1, false);
            return R.ok();
        } catch (MqttException e) {
            return R.fail("维护复位指令发布失败:" + e.getMessage());
        }
    }
}
