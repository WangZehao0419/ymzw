package com.ruoyi.ai.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.ai.entity.DiagnosisRecord;
import com.ruoyi.ai.entity.query.DiagnosisRecordQuery;
import com.ruoyi.ai.entity.vo.AiAgentVO;
import com.ruoyi.ai.entity.vo.DiagnosisResultVO;
import com.ruoyi.ai.enums.AgentTypeEnum;
import com.ruoyi.ai.service.AiAgentService;
import com.ruoyi.ai.service.ChatService;
import com.ruoyi.ai.service.DiagnosisRecordService;
import com.ruoyi.ai.service.DiagnosisService;
import com.ruoyi.common.core.web.domain.AjaxResult;
import com.ruoyi.common.core.web.page.TableDataInfo;
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
    private final DiagnosisService diagnosisService;
    private final DiagnosisRecordService diagnosisRecordService;

    /**
     * 设备诊断（B6：Qdrant RAG + 预测数据 + LLM 报告生成）
     * <p>
     * 诊断智能体不存在时由 DiagnosisService 用默认 ChatModel 兜底，
     * 保证比赛演示开箱即用（详见 DiagnosisServiceImpl.resolveChatModel 注释）。
     * </p>
     */
    @PostMapping("/diagnose")
    @Operation(summary = "设备诊断", description = "拉取预测数据+RAG知识检索，由诊断智能体生成Markdown诊断报告并落库")
    public AjaxResult diagnose(
            @Parameter(description = "设备ID") @RequestParam Integer equipmentId,
            @Parameter(description = "传感器编号(如TH-001)") @RequestParam String sensorCode) {

        log.info("设备诊断请求: 设备={}, 传感器={}", equipmentId, sensorCode);

        try {
            DiagnosisResultVO vo = diagnosisService.diagnose(equipmentId, sensorCode);
            // AjaxResult.success(data) 直接携带 VO 字段，前端可平铺取用
            return AjaxResult.success(vo);

        } catch (Exception e) {
            log.error("设备诊断失败: {}", e.getMessage(), e);
            return AjaxResult.error("设备诊断失败: " + e.getMessage());
        }
    }

    /**
     * 诊断报告历史查询（equipmentId/sensorCode 可选过滤，分页）
     */
    @GetMapping("/diagnose/records")
    @Operation(summary = "诊断报告历史", description = "分页查询诊断报告历史，支持按设备与传感器过滤")
    public TableDataInfo diagnoseRecords(DiagnosisRecordQuery query) {
        IPage<DiagnosisRecord> page = diagnosisRecordService.page(query);
        return new TableDataInfo(page.getRecords(), page.getTotal());
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
