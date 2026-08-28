package com.ruoyi.ai.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.ai.entity.dto.AiAgentDTO;
import com.ruoyi.ai.entity.query.AiAgentQuery;
import com.ruoyi.ai.entity.vo.AiAgentVO;
import com.ruoyi.ai.enums.AgentTypeEnum;
import com.ruoyi.ai.service.AiAgentService;
import com.ruoyi.common.core.web.domain.AjaxResult;
import com.ruoyi.common.core.web.page.TableDataInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AI智能体管理控制器
 * <p>
 * 提供AI智能体的增删改查等REST API接口
 * </p>
 *
 * @author smartartisan
 */
@Tag(name = "AI智能体管理", description = "AI智能体的增删改查相关接口")
@RestController
@RequestMapping("/ai/agent")
@RequiredArgsConstructor
public class AiAgentController {

    private final AiAgentService aiAgentService;

    /**
     * 分页查询智能体列表
     *
     * @param query 查询参数
     * @return 分页结果
     */
    @Operation(summary = "分页查询智能体列表", description = "支持按智能体名称、类型、状态等条件筛选")
    @GetMapping("/page")
    public TableDataInfo page(AiAgentQuery query) {
        IPage<AiAgentVO> page = aiAgentService.page(query);
        // 将 MyBatis-Plus 分页结果转换为若依前端约定的 TableDataInfo 结构（rows/total）
        return new TableDataInfo(page.getRecords(), page.getTotal());
    }

    /**
     * 根据ID查询智能体详情
     *
     * @param id 智能体ID
     * @return 智能体详情
     */
    @Operation(summary = "根据ID查询智能体详情", description = "获取智能体详细信息")
    @GetMapping("/{id}")
    public AjaxResult getById(
            @Parameter(description = "智能体ID") @PathVariable Long id) {
        AiAgentVO vo = aiAgentService.getDetailById(id);
        if (vo == null) {
            return AjaxResult.error("智能体不存在");
        }
        return AjaxResult.success(vo);
    }

    /**
     * 新增智能体
     *
     * @param dto 智能体信息
     * @return 操作结果
     */
    @Operation(summary = "新增智能体", description = "新增AI智能体配置，API Key会自动加密存储")
    @PostMapping
    public AjaxResult add(@Valid @RequestBody AiAgentDTO dto) {
        aiAgentService.addAgent(dto);
        return AjaxResult.success();
    }

    /**
     * 更新智能体
     * <p>
     * 特殊处理：如果API Key传入脱敏值（如***），则保持原有密钥不变
     * </p>
     *
     * @param dto 智能体信息
     * @return 操作结果
     */
    @Operation(summary = "更新智能体", description = "更新AI智能体配置。若API Key传入脱敏值（如***），则保持原有密钥不变")
    @PutMapping
    public AjaxResult update(@Valid @RequestBody AiAgentDTO dto) {
        aiAgentService.updateAgent(dto);
        return AjaxResult.success();
    }

    /**
     * 删除智能体
     *
     * @param id 智能体ID
     * @return 操作结果
     */
    @Operation(summary = "删除智能体", description = "逻辑删除智能体配置")
    @DeleteMapping("/{id}")
    public AjaxResult delete(
            @Parameter(description = "智能体ID") @PathVariable Long id) {
        aiAgentService.deleteAgent(id);
        return AjaxResult.success();
    }

    /**
     * 批量删除智能体
     *
     * @param ids 智能体ID列表，逗号分隔
     * @return 操作结果
     */
    @Operation(summary = "批量删除智能体", description = "批量逻辑删除智能体配置")
    @DeleteMapping("/batch/{ids}")
    public AjaxResult batchDelete(
            @Parameter(description = "智能体ID列表，逗号分隔") @PathVariable String ids) {
        List<Long> idList = Arrays.stream(ids.split(","))
                .map(String::trim)
                .map(Long::parseLong)
                .collect(Collectors.toList());
        aiAgentService.batchDelete(idList);
        return AjaxResult.success();
    }

    /**
     * 更新智能体状态
     *
     * @param id     智能体ID
     * @param status 状态（0-禁用，1-启用）
     * @return 操作结果
     */
    @Operation(summary = "更新智能体状态", description = "启用或禁用智能体")
    @PutMapping("/status/{id}")
    public AjaxResult updateStatus(
            @Parameter(description = "智能体ID") @PathVariable Long id,
            @Parameter(description = "状态（0-禁用，1-启用）") @RequestParam Integer status) {
        aiAgentService.updateStatus(id, status);
        return AjaxResult.success();
    }

    /**
     * 获取智能体类型枚举列表
     *
     * @return 智能体类型列表
     */
    @Operation(summary = "获取智能体类型列表", description = "获取系统支持的智能体类型枚举")
    @GetMapping("/types")
    public AjaxResult getAgentTypes() {
        return AjaxResult.success(Arrays.asList(AgentTypeEnum.values()));
    }

    /**
     * 获取指定类型的启用智能体列表
     *
     * @param agentType 智能体类型（可选）
     * @return 智能体列表
     */
    @Operation(summary = "获取启用智能体列表", description = "获取指定类型或所有启用的智能体列表")
    @GetMapping("/enabled")
    public AjaxResult getEnabledAgents(
            @Parameter(description = "智能体类型（可选）") @RequestParam(required = false) String agentType) {
        List<AiAgentVO> agents = aiAgentService.getAllAgentsByType(agentType);
        return AjaxResult.success(agents);
    }

    /**
     * 检查智能体名称是否唯一
     *
     * @param agentName 智能体名称
     * @param id        智能体ID（更新时传入，新增时不传）
     * @return 是否唯一
     */
    @Operation(summary = "检查智能体名称唯一性", description = "验证智能体名称是否已存在")
    @GetMapping("/check-name")
    public AjaxResult checkName(
            @Parameter(description = "智能体名称") @RequestParam String agentName,
            @Parameter(description = "智能体ID（更新时传入）") @RequestParam(required = false) Long id) {
        boolean unique = aiAgentService.checkAgentNameUnique(agentName, id);
        return AjaxResult.success(unique);
    }
}