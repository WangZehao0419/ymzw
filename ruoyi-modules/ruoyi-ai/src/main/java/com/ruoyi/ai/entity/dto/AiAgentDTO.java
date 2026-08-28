package com.ruoyi.ai.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * AI智能体数据传输对象
 * <p>
 * 用于接收前端提交的智能体新增和更新请求参数
 * </p>
 *
 * @author smartartisan
 */
@Data
@Schema(description = "AI智能体数据传输对象")
public class AiAgentDTO {

    /**
     * 主键ID（更新时必填）
     */
    @Schema(description = "主键ID（更新时必填）")
    private Long id;

    /**
     * 智能体名称
     */
    @NotBlank(message = "智能体名称不能为空")
    @Schema(description = "智能体名称", example = "设备诊断专家")
    private String agentName;

    /**
     * 智能体类型
     */
    @NotBlank(message = "智能体类型不能为空")
    @Schema(description = "智能体类型", example = "SENSOR_ALERT")
    private String agentType;

    /**
     * 头像URL
     */
    @Schema(description = "头像URL")
    private String agentAvatar;

    /**
     * 系统提示词
     */
    @Schema(description = "系统提示词")
    private String systemPrompt;

    /**
     * API地址
     */
    @NotBlank(message = "API地址不能为空")
    @Schema(description = "API地址", example = "https://api.deepseek.com/v1")
    private String apiEndpoint;

    /**
     * API密钥（新增时必填，更新时传脱敏值保持原密钥）
     */
    @Schema(description = "API密钥（新增时必填，更新时传脱敏值保持原密钥）")
    private String apiKey;

    /**
     * 基座模型标识符
     */
    @NotBlank(message = "模型标识符不能为空")
    @Schema(description = "基座模型标识符", example = "deepseek-chat")
    private String modelIdentifier;

    /**
     * 温度参数（0-2）
     */
    @Min(value = 0, message = "温度参数不能小于0")
    @Max(value = 2, message = "温度参数不能大于2")
    @Schema(description = "温度参数(0-2)", example = "0.7")
    private Double temperature;

    /**
     * 工具配置（JSON格式）
     */
    @Schema(description = "工具配置(JSON)")
    private String toolsConfig;

    /**
     * 关联知识库ID列表（逗号分隔）
     */
    @Schema(description = "关联知识库ID列表(逗号分隔)")
    private String knowledgeBaseIds;

    /**
     * 描述
     */
    @Schema(description = "描述")
    private String description;

    /**
     * 状态（0-禁用，1-启用）
     */
    @Schema(description = "状态(0-禁用,1-启用)", example = "1")
    private Integer status;

    /**
     * 排序号
     */
    @Schema(description = "排序号", example = "1")
    private Integer sortOrder;

    /**
     * 备注
     */
    @Schema(description = "备注")
    private String remark;
}