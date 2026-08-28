package com.ruoyi.ai.controller;

import com.ruoyi.ai.entity.vo.AiAgentVO;
import com.ruoyi.ai.enums.AgentTypeEnum;
import com.ruoyi.ai.service.AiAgentService;
import com.ruoyi.ai.service.ChatService;
import com.ruoyi.common.core.web.domain.AjaxResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 智能体业务服务控制器
 * <p>
 * 按业务类型路由到对应智能体进行AI处理，
 * 不同业务调用不同智能体，实现业务与智能体的解耦
 * </p>
 *
 * @author ruoyi
 */
@Slf4j
@RestController
@RequestMapping("/ai/agent-service")
@RequiredArgsConstructor
@Tag(name = "智能体业务服务", description = "按业务类型路由到对应智能体进行AI处理")
public class AgentServiceController {

    private final AiAgentService aiAgentService;
    private final ChatService chatService;

    @PostMapping("/predictive-alarm")
    @Operation(summary = "预测性告警", description = "使用告警助手智能体进行时序预测性告警")
    public AjaxResult predictiveAlarm(
            @Parameter(description = "设备ID") @RequestParam Integer equipmentId,
            @Parameter(description = "传感器ID") @RequestParam Integer sensorId,
            @Parameter(description = "传感器数值") @RequestParam Double sensorValue) {

        log.info("预测性告警请求: 设备={}, 传感器={}, 数值={}", equipmentId, sensorId, sensorValue);

        try {
            AiAgentVO agent = aiAgentService.getEnabledAgentByType(AgentTypeEnum.PREDICTIVE_ALARM.getCode());
            if (agent == null) {
                return AjaxResult.error("没有可用的预测性告警智能体");
            }

            log.info("使用预测性告警智能体: {} (ID: {})", agent.getAgentName(), agent.getId());

            String content = chatService.predictiveAlarm(agent, equipmentId, sensorId, sensorValue);

            Map<String, Object> result = new HashMap<>();
            result.put("agentId", agent.getId());
            result.put("agentName", agent.getAgentName());
            result.put("equipmentId", equipmentId);
            result.put("sensorId", sensorId);
            result.put("sensorValue", sensorValue);
            result.put("response", content);

            return AjaxResult.success(result);

        } catch (Exception e) {
            log.error("预测性告警失败: {}", e.getMessage(), e);
            return AjaxResult.error("预测性告警失败: " + e.getMessage());
        }
    }

    @PostMapping("/part-inspection")
    @Operation(summary = "零件检测", description = "使用零件检测助手智能体进行零件AI检测")
    public AjaxResult partInspection(
            @Parameter(description = "检测消息") @RequestParam String message) {

        log.info("零件检测请求，消息长度: {}", message.length());

        try {
            AiAgentVO agent = aiAgentService.getEnabledAgentByType(AgentTypeEnum.PART_INSPECTION.getCode());
            if (agent == null) {
                return AjaxResult.error("没有可用的零件检测智能体");
            }

            log.info("使用零件检测智能体: {} (ID: {})", agent.getAgentName(), agent.getId());

            String content = chatService.chatStream(agent.getId(), message)
                    .collect(Collectors.joining())
                    .block();

            Map<String, Object> result = new HashMap<>();
            result.put("agentId", agent.getId());
            result.put("agentName", agent.getAgentName());
            result.put("message", message);
            result.put("response", content);

            return AjaxResult.success(result);

        } catch (Exception e) {
            log.error("零件检测失败: {}", e.getMessage(), e);
            return AjaxResult.error("零件检测失败: " + e.getMessage());
        }
    }
}
