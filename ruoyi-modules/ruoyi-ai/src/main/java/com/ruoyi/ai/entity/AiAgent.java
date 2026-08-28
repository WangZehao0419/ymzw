package com.ruoyi.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI智能体实体类
 * <p>
 * 用于存储AI智能体配置，每个智能体关联一个基座模型并提供
 * 系统提示词、知识库等专属配置
 * </p>
 *
 * @author smartartisan
 */
@Data
@TableName("ai_agent")
public class AiAgent {

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    // --- 智能体身份 ---

    /**
     * 智能体名称
     */
    @TableField("agent_name")
    private String agentName;

    /**
     * 智能体类型
     */
    @TableField("agent_type")
    private String agentType;

    /**
     * 头像URL
     */
    @TableField("agent_avatar")
    private String agentAvatar;

    /**
     * 系统提示词
     */
    @TableField("system_prompt")
    private String systemPrompt;

    // --- 基座模型接入配置 ---

    /**
     * API地址
     */
    @TableField("api_endpoint")
    private String apiEndpoint;

    /**
     * API密钥
     */
    @TableField("api_key")
    private String apiKey;

    /**
     * 基座模型标识符
     */
    @TableField("model_identifier")
    private String modelIdentifier;

    /**
     * 温度参数（0-2）
     */
    @TableField("temperature")
    private Double temperature;

    // --- 智能体专属字段 ---

    /**
     * 工具配置（JSON格式）
     */
    @TableField("tools_config")
    private String toolsConfig;

    /**
     * 关联知识库ID列表（逗号分隔）
     */
    @TableField("knowledge_base_ids")
    private String knowledgeBaseIds;

    // --- 通用字段 ---

    /**
     * 描述
     */
    @TableField("description")
    private String description;

    /**
     * 状态（0-禁用，1-启用）
     */
    @TableField("status")
    private Integer status;

    /**
     * 排序号
     */
    @TableField("sort_order")
    private Integer sortOrder;

    /**
     * 备注
     */
    @TableField("remark")
    private String remark;

    /**
     * 创建时间
     */
    @TableField(value = "create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(value = "update_time")
    private LocalDateTime updateTime;

    /**
     * 创建人
     */
    @TableField("create_user")
    private String createUser;

    /**
     * 更新人
     */
    @TableField("update_user")
    private String updateUser;

    /**
     * 删除标志（0-未删除，1-已删除）
     */
    @TableLogic
    @TableField("delete_flag")
    private Integer deleteFlag;
}