package com.ruoyi.ai.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI智能体视图对象
 * <p>
 * 用于前端展示智能体详情
 * </p>
 *
 * @author smartartisan
 */
@Data
@Schema(description = "AI智能体视图对象")
public class AiAgentVO {

    /**
     * 主键ID
     */
    @Schema(description = "主键ID")
    private Long id;

    /**
     * 智能体名称
     */
    @Schema(description = "智能体名称")
    private String agentName;

    /**
     * 智能体类型
     */
    @Schema(description = "智能体类型")
    private String agentType;

    /**
     * 智能体类型描述
     */
    @Schema(description = "智能体类型描述")
    private String agentTypeDesc;

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

    // --- 基座模型字段 ---

    /**
     * API地址
     */
    @Schema(description = "API地址")
    private String apiEndpoint;

    /**
     * API密钥
     */
    @Schema(description = "API密钥")
    private String apiKey;

    /**
     * 基座模型标识符
     */
    @Schema(description = "基座模型标识符")
    private String modelIdentifier;

    /**
     * 温度参数
     */
    @Schema(description = "温度参数")
    private Double temperature;

    // --- 智能体专属字段 ---

    /**
     * 工具配置（JSON格式）
     */
    @Schema(description = "工具配置(JSON)")
    private String toolsConfig;

    /**
     * 关联知识库ID列表
     */
    @Schema(description = "关联知识库ID列表")
    private String knowledgeBaseIds;

    // --- 通用字段 ---

    /**
     * 描述
     */
    @Schema(description = "描述")
    private String description;

    /**
     * 状态（0-禁用，1-启用）
     */
    @Schema(description = "状态(0-禁用,1-启用)")
    private Integer status;

    /**
     * 状态描述
     */
    @Schema(description = "状态描述")
    private String statusDesc;

    /**
     * 排序号
     */
    @Schema(description = "排序号")
    private Integer sortOrder;

    /**
     * 备注
     */
    @Schema(description = "备注")
    private String remark;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @Schema(description = "更新时间")
    private LocalDateTime updateTime;

    /**
     * 创建人
     */
    @Schema(description = "创建人")
    private String createUser;

    /**
     * 更新人
     */
    @Schema(description = "更新人")
    private String updateUser;
}