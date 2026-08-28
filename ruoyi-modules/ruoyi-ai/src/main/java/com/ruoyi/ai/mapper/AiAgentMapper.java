package com.ruoyi.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.ai.entity.AiAgent;
import com.ruoyi.ai.entity.query.AiAgentQuery;
import com.ruoyi.ai.entity.vo.AiAgentVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * AI智能体Mapper接口
 * <p>
 * 提供AI智能体的数据库操作方法
 * </p>
 *
 * @author smartartisan
 */
@Mapper
public interface AiAgentMapper extends BaseMapper<AiAgent> {

    /**
     * 分页查询智能体列表
     *
     * @param page  分页对象
     * @param query 查询参数
     * @return 分页结果
     */
    IPage<AiAgentVO> selectAgentPage(IPage<AiAgentVO> page, @Param("query") AiAgentQuery query);

    /**
     * 根据ID查询智能体详情
     *
     * @param id 智能体ID
     * @return 智能体详情
     */
    AiAgentVO selectAgentById(@Param("id") Long id);

    /**
     * 检查智能体名称是否唯一
     *
     * @param agentName 智能体名称
     * @param excludeId 排除的ID
     * @return 数量
     */
    int countByAgentName(@Param("agentName") String agentName, @Param("excludeId") Long excludeId);

    /**
     * 行锁查询指定类型的所有智能体
     * <p>
     * 用于并发启用时的串行化控制，防止同一类型多个 Agent 同时启用。
     * SELECT ... FOR UPDATE 会对该类型所有行加排他锁，其他事务必须等待。
     * </p>
     *
     * @param agentType 智能体类型
     * @return 该类型所有未删除的 Agent 列表
     */
    @Select("SELECT id, agent_name, agent_type, status FROM ai_agent WHERE agent_type = #{agentType} AND delete_flag = 0 FOR UPDATE")
    List<AiAgent> selectForUpdateByType(@Param("agentType") String agentType);

    }