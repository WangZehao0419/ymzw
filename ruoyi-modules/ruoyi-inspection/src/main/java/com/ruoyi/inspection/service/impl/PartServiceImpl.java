package com.ruoyi.inspection.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.inspection.repository.BaseRepository;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.inspection.entity.Part;
import com.ruoyi.inspection.entity.dto.PartDTO;
import com.ruoyi.inspection.entity.query.PartInspectionQuery;
import com.ruoyi.inspection.entity.vo.PartInspectionVO;
import com.ruoyi.inspection.entity.vo.PartVO;
import com.ruoyi.inspection.mapper.PartMapper;
import com.ruoyi.inspection.service.PartService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 零件 Service 实现类
 * <p>
 * 继承 MyBatis-Plus 的 ServiceImpl，实现业务逻辑
 * </p>
 *
 * @author smartartisan
 */
@Service
public class PartServiceImpl extends BaseRepository<PartMapper, Part> implements PartService {
    @org.springframework.beans.factory.annotation.Autowired
    private PartMapper baseMapper;

    @Override
    public PartMapper getBaseMapper() {
        return baseMapper;
    }


    @Override
    public Part getByPartCode(String partCode) {
        // 使用 LambdaQueryWrapper 进行条件查询
        LambdaQueryWrapper<Part> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Part::getPartCode, partCode);
        return this.getOne(wrapper);
    }

    @Override
    public List<Part> listByIds(List<Long> ids) {
        // 使用 MyBatis-Plus 提供的 listByIds 方法
        return this.listByIds(ids);
    }

    @Override
    public boolean updateInspectionResult(Long partId, Boolean isQualified, String inspectionDetails, String suggestion) {
        // 调用 Mapper 中自定义的更新方法
        return baseMapper.updateInspectionResult(partId, isQualified, inspectionDetails, suggestion) > 0;
    }

    @Override
    public IPage<PartInspectionVO> getPartInspectionPage(Page<PartInspectionVO> page, PartInspectionQuery query) {
        return baseMapper.selectPartInspectionPage(page, query);
    }

    /**
     * 新增零件
     * <p>
     * 验证零件编码唯一性后保存零件信息
     * </p>
     *
     * @param dto 零件信息，包含零件名称、编码、检测标准等
     * @return 是否成功，true-成功，false-失败
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean addPart(PartDTO dto) {
        // 验证零件编码唯一性
        Part existingPart = this.getByPartCode(dto.getPartCode());
        if (existingPart != null) {
            throw new ServiceException("零件编码已存在");
        }
        
        // 将DTO转换为实体
        Part part = new Part();
        BeanUtils.copyProperties(dto, part);
        
        // 保存零件信息
        return this.save(part);
    }

    /**
     * 更新零件
     * <p>
     * 验证零件存在性和编码唯一性后更新零件信息
     * </p>
     *
     * @param dto 零件信息，ID必填
     * @return 是否成功，true-成功，false-失败
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updatePart(PartDTO dto) {
        // 验证零件ID不能为空
        if (dto.getId() == null) {
            throw new ServiceException("零件ID不能为空");
        }
        
        // 验证零件是否存在
        Part existingPart = this.getById(dto.getId());
        if (existingPart == null) {
            throw new ServiceException("零件不存在");
        }
        
        // 如果修改了零件编码，验证新编码的唯一性（排除当前零件）
        if (!existingPart.getPartCode().equals(dto.getPartCode())) {
            Part partWithSameCode = this.getByPartCode(dto.getPartCode());
            if (partWithSameCode != null) {
                throw new ServiceException("零件编码已存在");
            }
        }
        
        // 将DTO转换为实体
        Part part = new Part();
        BeanUtils.copyProperties(dto, part);
        
        // 更新零件信息
        return this.updateById(part);
    }

    /**
     * 根据ID查询零件详情
     * <p>
     * 根据零件ID查询零件的完整信息
     * </p>
     *
     * @param id 零件ID
     * @return 零件详情，不存在时返回null
     */
    @Override
    public PartVO getPartDetailById(Long id) {
        // 查询零件信息
        Part part = this.getById(id);
        if (part == null) {
            return null;
        }
        
        // 将实体转换为VO
        PartVO vo = new PartVO();
        BeanUtils.copyProperties(part, vo);
        return vo;
    }
}
