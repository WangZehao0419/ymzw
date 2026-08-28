package com.ruoyi.inspection.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.inspection.repository.BaseRepository;
import com.ruoyi.inspection.entity.InspectionStandard;
import com.ruoyi.inspection.entity.dto.InspectionStandardDTO;
import com.ruoyi.inspection.entity.query.InspectionStandardQuery;
import com.ruoyi.inspection.entity.vo.InspectionStandardVO;
import com.ruoyi.inspection.mapper.InspectionStandardMapper;
import com.ruoyi.inspection.service.InspectionStandardService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 检测标准 Service 实现类
 * <p>
 * 继承 MyBatis-Plus 的 ServiceImpl，实现业务逻辑
 * </p>
 *
 * @author smartartisan
 */
@Service
public class InspectionStandardServiceImpl extends BaseRepository<InspectionStandardMapper, InspectionStandard> implements InspectionStandardService {
    @org.springframework.beans.factory.annotation.Autowired
    private InspectionStandardMapper baseMapper;

    @Override
    public InspectionStandardMapper getBaseMapper() {
        return baseMapper;
    }


    @Override
    public InspectionStandard getByPartType(String partType) {
        // 使用 LambdaQueryWrapper 进行条件查询
        LambdaQueryWrapper<InspectionStandard> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(InspectionStandard::getPartType, partType);
        return this.getOne(wrapper);
    }

    @Override
    public Page<InspectionStandardVO> pageQuery(InspectionStandardQuery query) {
        // 构建分页对象
        Page<InspectionStandard> page = new Page<>(query.getPage(), query.getPageSize());
        
        // 构建查询条件
        LambdaQueryWrapper<InspectionStandard> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(query.getPartType()), InspectionStandard::getPartType, query.getPartType())
                .like(StringUtils.hasText(query.getStandardName()), InspectionStandard::getStandardName, query.getStandardName())
                .orderByDesc(InspectionStandard::getCreateTime);
        
        // 执行分页查询
        Page<InspectionStandard> entityPage = this.page(page, wrapper);
        
        // 转换为VO分页对象
        Page<InspectionStandardVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(entityPage.getRecords().stream()
                .map(this::convertToVO)
                .toList());
        
        return voPage;
    }

    @Override
    public boolean addStandard(InspectionStandardDTO dto) {
        InspectionStandard entity = convertToEntity(dto);
        return this.save(entity);
    }

    @Override
    public boolean updateStandard(InspectionStandardDTO dto) {
        if (dto.getId() == null) {
            return false;
        }
        InspectionStandard entity = convertToEntity(dto);
        return this.updateById(entity);
    }

    @Override
    public InspectionStandardVO getDetailById(Long id) {
        InspectionStandard entity = this.getById(id);
        if (entity == null) {
            return null;
        }
        return convertToVO(entity);
    }

    /**
     * 将实体转换为视图对象
     *
     * @param entity 检测标准实体
     * @return 视图对象
     */
    private InspectionStandardVO convertToVO(InspectionStandard entity) {
        if (entity == null) {
            return null;
        }
        InspectionStandardVO vo = new InspectionStandardVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }

    /**
     * 将DTO转换为实体
     *
     * @param dto 数据传输对象
     * @return 实体对象
     */
    private InspectionStandard convertToEntity(InspectionStandardDTO dto) {
        if (dto == null) {
            return null;
        }
        InspectionStandard entity = new InspectionStandard();
        BeanUtils.copyProperties(dto, entity);
        return entity;
    }
}
