package com.ruoyi.equipment.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * AI 服务 Feign 客户端
 * <p>
 * 远程调用 cloud-ai 微服务的预测性告警接口。
 * 异步调用，不阻塞 MQTT 回调线程。
 * </p>
 *
 * @author smartartisan
 */
@FeignClient(name = "cloud-ai", contextId = "equipmentAiFeignClient")
public interface AiServiceFeignClient {

    /**
     * 调用预测性告警智能体
     * <p>
     * cloud-ai 内部通过 ReactAgent + Redis 短期记忆进行时序分析，
     * 返回预测结论和告警级别。
     * </p>
     *
     * @param equipmentId 设备 ID
     * @param sensorId    传感器 ID（数据库主键）
     * @param sensorValue 传感器当前值
     * @return 包含 success、response、agentId、agentName 的 Map
     */
    @PostMapping("/ai/agent-service/predictive-alarm")
    Map<String, Object> predictiveAlarm(@RequestParam("equipmentId") Integer equipmentId,
                                         @RequestParam("sensorId") Integer sensorId,
                                         @RequestParam("sensorValue") Double sensorValue);
}
