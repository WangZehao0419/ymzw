package com.ruoyi.ai.entity.query;

import lombok.Data;

/**
 * AI智能体分页查询参数
 * <p>
 * 用于智能体列表的条件筛选和分页查询
 * </p>
 *
 * @author smartartisan
 */
@Data
public class AiAgentQuery {

    /**
     * 当前页码
     */
    private Integer page = 1;

    /**
     * 每页条数
     */
    private Integer pageSize = 10;

    /**
     * 智能体名称（模糊查询）
     */
    private String agentName;

    /**
     * 智能体类型
     */
    private String agentType;

    /**
     * 状态（0-禁用，1-启用）
     */
    private Integer status;
}