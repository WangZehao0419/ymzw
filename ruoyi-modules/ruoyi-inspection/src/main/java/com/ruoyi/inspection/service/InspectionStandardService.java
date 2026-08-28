package com.ruoyi.inspection.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.repository.IRepository;
import com.ruoyi.inspection.entity.InspectionStandard;
import com.ruoyi.inspection.entity.dto.InspectionStandardDTO;
import com.ruoyi.inspection.entity.query.InspectionStandardQuery;
import com.ruoyi.inspection.entity.vo.InspectionStandardVO;

/**
 * 检测标准 Service 接口
 * <p>
 * 继承 MyBatis-Plus 的 IService，自动拥有 CRUD 功能
 * </p>
 *
 * @author smartartisan
 */
public interface InspectionStandardService extends IRepository<InspectionStandard> {

    /**
     * 根据零件类型查询检测标准
     *
     * @param partType 零件类型
     * @return 检测标准
     */
    InspectionStandard getByPartType(String partType);

    /**
     * 分页查询检测标准列表
     *
     * @param query 查询参数
     * @return 分页结果
     */
    Page<InspectionStandardVO> pageQuery(InspectionStandardQuery query);

    /**
     * 新增检测标准
     *
     * @param dto 检测标准信息
     * @return 是否成功
     */
    boolean addStandard(InspectionStandardDTO dto);

    /**
     * 更新检测标准
     *
     * @param dto 检测标准信息
     * @return 是否成功
     */
    boolean updateStandard(InspectionStandardDTO dto);

    /**
     * 根据ID获取检测标准详情
     *
     * @param id 检测标准ID
     * @return 检测标准详情
     */
    InspectionStandardVO getDetailById(Long id);
}
