package com.ruoyi.equipment.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.common.core.web.domain.AjaxResult;
import com.ruoyi.common.core.web.page.TableDataInfo;
import com.ruoyi.equipment.entity.query.MonitorDataQuery;
import com.ruoyi.equipment.entity.vo.MonitorDataVO;
import com.ruoyi.equipment.service.EquipmentSensorMonitorService;
import com.ruoyi.equipment.stream.SensorStreamConnectionPool;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 设备监控 Controller
 * <p>
 * 提供监测数据上报、实时数据查询、历史数据查询等接口
 * </p>
 *
 * @author smartartisan
 */
@RestController
@RequestMapping("/equipment/monitor")
@RequiredArgsConstructor
public class EquipmentSensorMonitorController {

    private final EquipmentSensorMonitorService monitorService;
    private final SensorStreamConnectionPool streamPool;

    /**
     * 查询传感器实时数据
     *
     * @param sensorId 传感器ID
     * @return 最新监测数据
     */
    @GetMapping("/realtime/sensor/{sensorId}")
    public AjaxResult getRealtimeBySensor(@PathVariable Integer sensorId) {
        MonitorDataVO vo = monitorService.getRealtimeBySensorId(sensorId);
        if (vo == null) {
            return AjaxResult.error("暂无该传感器的监测数据");
        }
        return AjaxResult.success(vo);
    }

    /**
     * 查询设备实时状态（所有传感器最新数据）
     *
     * @param equipmentId 设备ID
     * @return 传感器最新监测数据列表
     */
    @GetMapping("/realtime/equipment/{equipmentId}")
    public AjaxResult getRealtimeByEquipment(@PathVariable Integer equipmentId) {
        List<MonitorDataVO> list = monitorService.getRealtimeByEquipmentId(equipmentId);
        return AjaxResult.success(list);
    }

    /**
     * 查询历史数据（支持时间范围）
     *
     * @param query 查询参数
     * @return 分页结果
     */
    @GetMapping("/history")
    public TableDataInfo queryHistory(MonitorDataQuery query) {
        IPage<MonitorDataVO> page = monitorService.queryHistory(query);
        return new TableDataInfo(page.getRecords(), page.getTotal());
    }

    /**
     * NDJSON 流式推送：订阅设备的实时传感器数据
     * <p>
     * 客户端连接此端点后，每当 MQTT 收到该设备传感器的数据，
     * 服务端会以 NDJSON（每行一个 JSON 对象）的形式持续推送给客户端，无需轮询。
     * 鉴权由 StreamAuthInterceptor 在 /stream/** 路径上校验 Bearer JWT。
     * </p>
     *
     * @param equipmentId 设备 ID
     * @param response    HTTP 响应（用于设置代理透传头）
     * @return ResponseBodyEmitter 长连接
     */
    @GetMapping(value = "/stream/{equipmentId}", produces = "application/x-ndjson")
    public ResponseBodyEmitter streamSensorData(@PathVariable Integer equipmentId,
                                                HttpServletResponse response) {
        // 禁用 Nginx 反向代理缓冲，保证每行数据立即到达客户端而不是攒够缓冲区才发
        response.setHeader("X-Accel-Buffering", "no");

        // 0L 表示永不超时，连接生命周期由心跳与客户端断开控制
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(0L);
        streamPool.add(equipmentId, emitter);

        // return 之后 emitter 才被 Spring MVC 接管，此时 send 才有效
        // 立即发一行初始心跳，冲开网关/浏览器缓冲，让客户端尽早确认连接已建立
        CompletableFuture.runAsync(() ->
                streamPool.send(equipmentId, Map.of("type", "heartbeat")));

        return emitter;
    }
}
