package com.ruoyi.ai.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.repository.IRepository;
import com.ruoyi.ai.entity.AiAgent;
import com.ruoyi.ai.entity.dto.AiAgentDTO;
import com.ruoyi.ai.entity.query.AiAgentQuery;
import com.ruoyi.ai.entity.vo.AiAgentVO;

import java.util.List;

/**
 * AI智能体服务接口
 * <p>
 * 提供AI智能体的增删改查等业务操作，
 * 每个智能体关联一个基座模型并提供系统提示词、知识库等专属配置
 * </p>
 *
 * @author smartartisan
 */
public interface AiAgentService extends IRepository<AiAgent> {

    /**
     * 分页查询智能体列表
     *
     * @param query 查询参数
     * @return 分页结果
     */
    IPage<AiAgentVO> page(AiAgentQuery query);

    /**
     * 根据ID查询智能体详情
     *
     * @param id 智能体ID
     * @return 智能体详情
     */
    AiAgentVO getDetailById(Long id);

    /**
     * 新增智能体
     *
     * @param dto 智能体信息
     * @return 是否成功
     */
    boolean addAgent(AiAgentDTO dto);

    /**
     * 更新智能体
     *
     * @param dto 智能体信息
     * @return 是否成功
     */
    boolean updateAgent(AiAgentDTO dto);

    /**
     * 删除智能体（逻辑删除）
     *
     * @param id 智能体ID
     * @return 是否成功
     */
    boolean deleteAgent(Long id);

    /**
     * 批量删除智能体
     *
     * @param ids 智能体ID列表
     * @return 是否成功
     */
    boolean batchDelete(List<Long> ids);

    /**
     * 更新智能体状态
     *
     * @param id     智能体ID
     * @param status 状态（0-禁用，1-启用）
     * @return 是否成功
     */
    boolean updateStatus(Long id, Integer status);

    /**
     * 检查智能体名称是否唯一
     *
     * @param agentName 智能体名称
     * @param excludeId 排除的ID（更新时排除自身）
     * @return true-唯一，false-已存在
     */
    boolean checkAgentNameUnique(String agentName, Long excludeId);

    /**
     * 获取指定类型的所有智能体列表
     *
     * @param agentType 智能体类型
     * @return 智能体列表
     */
    List<AiAgentVO> getAllAgentsByType(String agentType);

    /**
     * 获取指定类型唯一启用的智能体
     * <p>
     * 每个类型只允许启用一个智能体，该方法直接返回该唯一记录
     * </p>
     *
     * @param agentType 智能体类型
     * @return 唯一启用的智能体，无可用智能体时返回 null
     */
    AiAgentVO getEnabledAgentByType(String agentType);
}