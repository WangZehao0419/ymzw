package com.ruoyi.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.ai.repository.BaseRepository;
import com.ruoyi.ai.entity.AiAgent;
import com.ruoyi.ai.entity.dto.AiAgentDTO;
import com.ruoyi.ai.entity.query.AiAgentQuery;
import com.ruoyi.ai.entity.vo.AiAgentVO;
import com.ruoyi.ai.enums.AgentTypeEnum;
import com.ruoyi.ai.enums.ModelStatusEnum;
import com.ruoyi.ai.mapper.AiAgentMapper;
import com.ruoyi.ai.service.AiAgentService;
import com.ruoyi.common.core.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * AI智能体服务实现类
 * <p>
 * 实现AI智能体的增删改查等业务操作，
 * 以及类型关联等功能
 * </p>
 *
 * @author smartartisan
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAgentServiceImpl extends BaseRepository<AiAgentMapper, AiAgent> implements AiAgentService {

    private final AiAgentMapper aiAgentMapper;

    @Override
    public AiAgentMapper getBaseMapper() {
        return aiAgentMapper;
    }

    /**
     * 分页查询智能体列表
     *
     * @param query 查询参数
     * @return 分页结果
     */
    @Override
    public IPage<AiAgentVO> page(AiAgentQuery query) {
        Page<AiAgentVO> page = new Page<>(query.getPage(), query.getPageSize());
        IPage<AiAgentVO> result = aiAgentMapper.selectAgentPage(page, query);
        // 填充枚举描述信息
        result.getRecords().forEach(this::fillEnumDesc);
        return result;
    }

    /**
     * 根据ID查询智能体详情
     *
     * @param id 智能体ID
     * @return 智能体详情
     */
    @Override
    public AiAgentVO getDetailById(Long id) {
        AiAgentVO vo = aiAgentMapper.selectAgentById(id);
        if (vo != null) {
            fillEnumDesc(vo);
        }
        return vo;
    }

    /**
     * 新增智能体
     *
     * @param dto 智能体信息
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addAgent(AiAgentDTO dto) {
        if (!AgentTypeEnum.isValidCode(dto.getAgentType())) {
            throw new ServiceException("无效的智能体类型");
        }
        if (!checkAgentNameUnique(dto.getAgentName(), null)) {
            throw new ServiceException("智能体名称已存在");
        }
        if (dto.getStatus() != null && !ModelStatusEnum.isValidCode(dto.getStatus())) {
            throw new ServiceException("无效的状态值");
        }

        AiAgent agent = new AiAgent();
        BeanUtils.copyProperties(dto, agent);
        if (agent.getStatus() == null) {
            agent.setStatus(ModelStatusEnum.ENABLED.getCode());
        }
        if (agent.getSortOrder() == null) {
            agent.setSortOrder(0);
        }
        boolean saved = this.save(agent);

        // 新增启用智能体时：先锁住该类型所有行防止并发，再禁用其他已启用的智能体
        if (saved && ModelStatusEnum.ENABLED.getCode().equals(agent.getStatus())) {
            lockAgentsByType(agent.getAgentType());
            disableOthersOfSameType(agent.getId(), agent.getAgentType());
            // 并发安全校验：确保该类型只有一个启用
            long enabledCount = this.count(new LambdaQueryWrapper<AiAgent>()
                    .eq(AiAgent::getAgentType, agent.getAgentType())
                    .eq(AiAgent::getStatus, ModelStatusEnum.ENABLED.getCode()));
            if (enabledCount > 1) {
                throw new ServiceException("该类型已有启用的智能体，请稍后重试");
            }
        }
        return saved;
    }

    /**
     * 更新智能体
     * <p>
     * 使用 LambdaUpdateWrapper 显式控制每个字段的更新行为：
     * - 必填字段（agentName/agentType/apiEndpoint/modelIdentifier）只有 DTO 提供时才更新
     * - 可选文本字段传空串即清空，传 null 表示不修改
     * - apiKey 传空串时保持原值，传非空时更新
     * </p>
     *
     * @param dto 智能体信息
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateAgent(AiAgentDTO dto) {
        if (dto.getId() == null) {
            throw new ServiceException("智能体ID不能为空");
        }
        AiAgent existing = this.getById(dto.getId());
        if (existing == null) {
            throw new ServiceException("智能体不存在");
        }
        if (StringUtils.hasText(dto.getAgentType()) && !AgentTypeEnum.isValidCode(dto.getAgentType())) {
            throw new ServiceException("无效的智能体类型");
        }
        if (StringUtils.hasText(dto.getAgentName()) && !checkAgentNameUnique(dto.getAgentName(), dto.getId())) {
            throw new ServiceException("智能体名称已存在");
        }
        if (dto.getStatus() != null && !ModelStatusEnum.isValidCode(dto.getStatus())) {
            throw new ServiceException("无效的状态值");
        }

        // 确定生效的类型和状态（DTO 未提供时沿用现有值）
        String effectiveType = StringUtils.hasText(dto.getAgentType())
                ? dto.getAgentType() : existing.getAgentType();
        Integer effectiveStatus = dto.getStatus() != null
                ? dto.getStatus() : existing.getStatus();

        LambdaUpdateWrapper<AiAgent> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AiAgent::getId, dto.getId());

        // 必填字段：DTO 提供时才更新
        if (StringUtils.hasText(dto.getAgentName())) {
            wrapper.set(AiAgent::getAgentName, dto.getAgentName());
        }
        if (StringUtils.hasText(dto.getAgentType())) {
            wrapper.set(AiAgent::getAgentType, dto.getAgentType());
        }
        if (StringUtils.hasText(dto.getApiEndpoint())) {
            wrapper.set(AiAgent::getApiEndpoint, dto.getApiEndpoint());
        }
        if (StringUtils.hasText(dto.getModelIdentifier())) {
            wrapper.set(AiAgent::getModelIdentifier, dto.getModelIdentifier());
        }

        // apiKey 特殊处理：传空串保持原值
        if (StringUtils.hasText(dto.getApiKey())) {
            wrapper.set(AiAgent::getApiKey, dto.getApiKey());
        }

        // 可选模型参数
        if (dto.getTemperature() != null) {
            wrapper.set(AiAgent::getTemperature, dto.getTemperature());
        }

        // 可选文本字段：传 null 不修改，传空串清空
        if (dto.getAgentAvatar() != null) {
            wrapper.set(AiAgent::getAgentAvatar, dto.getAgentAvatar());
        }
        if (dto.getSystemPrompt() != null) {
            wrapper.set(AiAgent::getSystemPrompt, dto.getSystemPrompt());
        }
        if (dto.getToolsConfig() != null) {
            wrapper.set(AiAgent::getToolsConfig, dto.getToolsConfig());
        }
        if (dto.getKnowledgeBaseIds() != null) {
            wrapper.set(AiAgent::getKnowledgeBaseIds, dto.getKnowledgeBaseIds());
        }
        if (dto.getDescription() != null) {
            wrapper.set(AiAgent::getDescription, dto.getDescription());
        }
        if (dto.getRemark() != null) {
            wrapper.set(AiAgent::getRemark, dto.getRemark());
        }

        // 状态和排序
        if (dto.getStatus() != null) {
            wrapper.set(AiAgent::getStatus, dto.getStatus());
        }
        if (dto.getSortOrder() != null) {
            wrapper.set(AiAgent::getSortOrder, dto.getSortOrder());
        }

        boolean updated = this.update(wrapper);

        // 更新后若为启用状态：先锁该类型所有行，再禁用同类型下其他智能体
        if (updated && ModelStatusEnum.ENABLED.getCode().equals(effectiveStatus)) {
            lockAgentsByType(effectiveType);
            disableOthersOfSameType(dto.getId(), effectiveType);
        }
        return updated;
    }

    /**
     * 删除智能体（逻辑删除）
     *
     * @param id 智能体ID
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteAgent(Long id) {
        AiAgent agent = this.getById(id);
        if (agent == null) {
            throw new ServiceException("智能体不存在");
        }
        return this.removeById(id);
    }

    /**
     * 批量删除智能体
     *
     * @param ids 智能体ID列表
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new ServiceException("请选择要删除的智能体");
        }
        return this.removeByIds(ids);
    }

    /**
     * 更新智能体状态
     *
     * @param id     智能体ID
     * @param status 状态（0-禁用，1-启用）
     * @return 是否成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateStatus(Long id, Integer status) {
        AiAgent agent = this.getById(id);
        if (agent == null) {
            throw new ServiceException("智能体不存在");
        }
        if (!ModelStatusEnum.isValidCode(status)) {
            throw new ServiceException("无效的状态值");
        }

        // 启用时先对该类型所有行加排他锁，防止并发竞态
        if (ModelStatusEnum.ENABLED.getCode().equals(status)) {
            lockAgentsByType(agent.getAgentType());
            disableOthersOfSameType(id, agent.getAgentType());
        }

        LambdaUpdateWrapper<AiAgent> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(AiAgent::getId, id)
                     .set(AiAgent::getStatus, status);
        return this.update(updateWrapper);
    }

    /**
     * 禁用指定类型下除目标外所有已启用的智能体
     * <p>
     * 保证每个智能体类型最多只有一个启用状态
     * </p>
     */
    private void disableOthersOfSameType(Long targetId, String agentType) {
        LambdaUpdateWrapper<AiAgent> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AiAgent::getAgentType, agentType)
               .eq(AiAgent::getStatus, ModelStatusEnum.ENABLED.getCode())
               .ne(AiAgent::getId, targetId)
               .set(AiAgent::getStatus, ModelStatusEnum.DISABLED.getCode());
        this.update(wrapper);
        log.info("已禁用智能体类型 {} 下除 {} 外的其他智能体", agentType, targetId);
    }

    /**
     * 对指定类型的所有 Agent 行加排他锁
     * <p>
     * SELECT ... FOR UPDATE 对匹配行加行级排他锁，阻止其他事务并发修改。
     * 调用此方法后直到事务提交，其他事务对该类型 Agent 的 UPDATE/DELETE 将被阻塞。
     * 用于防止"每类型唯一启用"约束被并发绕过。
     * </p>
     *
     * @param agentType 智能体类型
     */
    private void lockAgentsByType(String agentType) {
        aiAgentMapper.selectForUpdateByType(agentType);
        log.debug("已对智能体类型 {} 加行锁", agentType);
    }

    /**
     * 检查智能体名称是否唯一
     *
     * @param agentName 智能体名称
     * @param excludeId 排除的ID
     * @return true-唯一，false-已存在
     */
    @Override
    public boolean checkAgentNameUnique(String agentName, Long excludeId) {
        return aiAgentMapper.countByAgentName(agentName, excludeId) == 0;
    }

    @Override
    public List<AiAgentVO> getAllAgentsByType(String agentType) {
        LambdaQueryWrapper<AiAgent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiAgent::getStatus, ModelStatusEnum.ENABLED.getCode());
        if (StringUtils.hasText(agentType)) {
            wrapper.eq(AiAgent::getAgentType, agentType);
        }
        wrapper.orderByAsc(AiAgent::getSortOrder);
        List<AiAgent> agents = this.list(wrapper);
        return agents.stream().map(agent -> {
            AiAgentVO vo = new AiAgentVO();
            BeanUtils.copyProperties(agent, vo);
            vo.setApiKey(agent.getApiKey());
            fillEnumDesc(vo);
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 获取指定类型唯一启用的智能体
     * <p>
     * 每个类型只允许启用一个智能体，按 sortOrder 升序取第一条
     * </p>
     *
     * @param agentType 智能体类型
     * @return 唯一启用的智能体，无可用智能体时返回 null
     */
    @Override
    public AiAgentVO getEnabledAgentByType(String agentType) {
        LambdaQueryWrapper<AiAgent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiAgent::getStatus, ModelStatusEnum.ENABLED.getCode());
        if (StringUtils.hasText(agentType)) {
            wrapper.eq(AiAgent::getAgentType, agentType);
        }
        wrapper.orderByAsc(AiAgent::getSortOrder);
        wrapper.last("LIMIT 1");

        AiAgent agent = this.getOne(wrapper);
        if (agent == null) {
            return null;
        }
        AiAgentVO vo = new AiAgentVO();
        BeanUtils.copyProperties(agent, vo);
        vo.setApiKey(agent.getApiKey());
        fillEnumDesc(vo);
        return vo;
    }

    /**
     * 填充枚举描述信息
     *
     * @param vo 视图对象
     */
    private void fillEnumDesc(AiAgentVO vo) {
        // 填充智能体类型描述
        AgentTypeEnum agentType = AgentTypeEnum.getByCode(vo.getAgentType());
        if (agentType != null) {
            vo.setAgentTypeDesc(agentType.getDescription());
        }
        // 填充状态描述
        ModelStatusEnum status = ModelStatusEnum.getByCode(vo.getStatus());
        if (status != null) {
            vo.setStatusDesc(status.getDescription());
        }
    }
}