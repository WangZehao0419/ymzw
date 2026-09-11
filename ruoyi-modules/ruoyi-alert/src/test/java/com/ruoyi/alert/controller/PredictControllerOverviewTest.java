package com.ruoyi.alert.controller;

import com.ruoyi.alert.controller.PredictController.EquipmentOverviewVO;
import com.ruoyi.alert.entity.PredictResult;
import com.ruoyi.equipment.api.domain.SensorMetaDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 设备级预测总览聚合纯函数单元测试(不起 Spring)
 *
 * @author smartartisan
 */
class PredictControllerOverviewTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 6, 12, 0, 0);

    @Test
    @DisplayName("状态优先级:任一 BREACHED > 任一 DEGRADING > 全 NORMAL")
    void statusPriority() {
        assertEquals("BREACHED", PredictController.aggregateStatus(
                List.of(snapshot("A", "NORMAL", null, null), snapshot("B", "BREACHED", null, null))));
        assertEquals("BREACHED", PredictController.aggregateStatus(
                List.of(snapshot("A", "DEGRADING", null, null), snapshot("B", "BREACHED", null, null))));
        assertEquals("DEGRADING", PredictController.aggregateStatus(
                List.of(snapshot("A", "NORMAL", null, null), snapshot("B", "DEGRADING", null, null))));
        assertEquals("NORMAL", PredictController.aggregateStatus(
                List.of(snapshot("A", "NORMAL", null, null), snapshot("B", "NORMAL", null, null))));
        assertEquals("NORMAL", PredictController.aggregateStatus(List.of()),
                "无快照成员应兜底 NORMAL");
    }

    @Test
    @DisplayName("设备健康分:healthScore 算术平均,null 跳过不按 0 计,全 null 返回 null")
    void healthAverageSkipsNull() {
        assertEquals(70.0, PredictController.averageHealth(
                List.of(snapshot("A", "NORMAL", 80.0, null), snapshot("B", "NORMAL", null, null),
                        snapshot("C", "NORMAL", 60.0, null))), 0.001);
        assertNull(PredictController.averageHealth(
                List.of(snapshot("A", "NORMAL", null, null))), "全 null 应返回 null");
        assertNull(PredictController.averageHealth(List.of()));
        // 保留 1 位小数(71.75 四舍五入为 71.8)
        assertEquals(71.8, PredictController.averageHealth(
                List.of(snapshot("A", "NORMAL", 80.0, null), snapshot("B", "NORMAL", 63.5, null))), 0.001);
    }

    @Test
    @DisplayName("剩余 RUL 换算:null 直通,未来为正,已过为负")
    void rulMinutesBoundaries() {
        assertNull(PredictController.rulMinutes(snapshot("A", "NORMAL", null, null), NOW));
        assertEquals(30L, PredictController.rulMinutes(
                snapshot("A", "DEGRADING", null, NOW.plusMinutes(30)), NOW));
        assertEquals(-10L, PredictController.rulMinutes(
                snapshot("A", "BREACHED", null, NOW.minusMinutes(10)), NOW),
                "失效时刻已过应为负(恰是最紧迫)");
        assertEquals(0L, PredictController.rulMinutes(
                snapshot("A", "DEGRADING", null, NOW), NOW));
    }

    @Test
    @DisplayName("按设备聚合:分组/状态/健康分均值/最紧迫 RUL 及对应传感器")
    void aggregateByEquipment() {
        SensorMetaDTO a1 = meta(10, "1号设备", "TEMP-001");
        SensorMetaDTO a2 = meta(10, "1号设备", "VIB-001");
        SensorMetaDTO b1 = meta(20, "2号设备", "TEMP-002");
        SensorMetaDTO c1 = meta(30, "3号设备", "HUM-001");  // 无快照:默认 NORMAL 不参与数值聚合

        Map<String, PredictResult> snapshots = new HashMap<>();
        snapshots.put("TEMP-001", snapshot("TEMP-001", "DEGRADING", 80.0, NOW.plusMinutes(120)));
        snapshots.put("VIB-001", snapshot("VIB-001", "NORMAL", 60.0, NOW.plusMinutes(40)));
        snapshots.put("TEMP-002", snapshot("TEMP-002", "BREACHED", 30.0, NOW.plusMinutes(90)));

        List<EquipmentOverviewVO> overview = PredictController.aggregateOverview(
                List.of(a1, a2, b1, c1), snapshots, NOW);

        assertEquals(3, overview.size(), "设备按元数据分组,无快照设备也进列表");
        // 1号设备:DEGRADING 优先于 NORMAL,健康分均值 (80+60)/2,最紧迫 RUL=40(VIB-001)
        EquipmentOverviewVO first = overview.get(0);
        assertEquals(10, first.getEquipmentId());
        assertEquals("1号设备", first.getEquipmentName());
        assertEquals("DEGRADING", first.getStatus());
        assertEquals(70.0, first.getHealthScore(), 0.001);
        assertEquals(40L, first.getMinRulPoint(), "最紧迫 RUL 应取剩余分钟最小值");
        assertEquals("VIB-001", first.getMinRulSensorCode());
        // 2号设备:BREACHED 最高优先级
        EquipmentOverviewVO second = overview.get(1);
        assertEquals("BREACHED", second.getStatus());
        assertEquals(30.0, second.getHealthScore(), 0.001);
        assertEquals(90L, second.getMinRulPoint());
        assertEquals("TEMP-002", second.getMinRulSensorCode());
        // 3号设备:无快照,默认 NORMAL,健康分与 RUL 无值
        EquipmentOverviewVO third = overview.get(2);
        assertEquals("NORMAL", third.getStatus());
        assertNull(third.getHealthScore());
        assertNull(third.getMinRulPoint());
        assertNull(third.getMinRulSensorCode());
    }

    @Test
    @DisplayName("最紧迫 RUL:无 RUL 传感器不参与,全无 RUL 时为 null")
    void minRulIgnoresNull() {
        SensorMetaDTO a1 = meta(10, "1号设备", "TEMP-001");
        SensorMetaDTO a2 = meta(10, "1号设备", "VIB-001");

        Map<String, PredictResult> snapshots = new HashMap<>();
        snapshots.put("TEMP-001", snapshot("TEMP-001", "DEGRADING", 80.0, null));
        snapshots.put("VIB-001", snapshot("VIB-001", "DEGRADING", 60.0, NOW.plusMinutes(40)));

        EquipmentOverviewVO vo = PredictController.aggregateOverview(
                List.of(a1, a2), snapshots, NOW).get(0);

        assertEquals(40L, vo.getMinRulPoint(), "无 RUL(NORMAL 清空)传感器应被跳过");
        assertEquals("VIB-001", vo.getMinRulSensorCode());

        // 全无 RUL
        snapshots.put("VIB-001", snapshot("VIB-001", "DEGRADING", 60.0, null));
        EquipmentOverviewVO allNull = PredictController.aggregateOverview(
                List.of(a1, a2), snapshots, NOW).get(0);
        assertNull(allNull.getMinRulPoint());
        assertNull(allNull.getMinRulSensorCode());
    }

    private SensorMetaDTO meta(int equipmentId, String equipmentName, String sensorCode) {
        SensorMetaDTO m = new SensorMetaDTO();
        m.setId(equipmentId * 10);
        m.setEquipmentId(equipmentId);
        m.setEquipmentName(equipmentName);
        m.setSensorCode(sensorCode);
        m.setSensorName(sensorCode + "传感器");
        return m;
    }

    /**
     * 构造快照:状态 + 健康分 + 预计失效时刻(健康分/失效时刻可为 null)
     */
    private PredictResult snapshot(String sensorCode, String status, Double healthScore,
                                   LocalDateTime predictedBreachTime) {
        PredictResult r = new PredictResult();
        r.setSensorCode(sensorCode);
        r.setStatus(status);
        r.setHealthScore(healthScore);
        r.setPredictedBreachTime(predictedBreachTime);
        return r;
    }
}
