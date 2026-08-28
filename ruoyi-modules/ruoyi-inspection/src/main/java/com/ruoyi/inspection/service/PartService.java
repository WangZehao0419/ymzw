package com.ruoyi.inspection.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.repository.IRepository;
import com.ruoyi.inspection.entity.Part;
import com.ruoyi.inspection.entity.dto.PartDTO;
import com.ruoyi.inspection.entity.query.PartInspectionQuery;
import com.ruoyi.inspection.entity.vo.PartInspectionVO;
import com.ruoyi.inspection.entity.vo.PartVO;

import java.util.List;

/**
 * 零件 Service 接口
 * <p>
 * 继承 MyBatis-Plus 的 IService，自动拥有 CRUD 功能
 * </p>
 *
 * @author smartartisan
 */
public interface PartService extends IRepository<Part> {

    /**
     * 根据零件编码查询零件
     *
     * @param partCode 零件编码
     * @return 零件信息
     */
    Part getByPartCode(String partCode);

    /**
     * 根据ID列表批量查询零件
     *
     * @param ids ID列表
     * @return 零件列表
     */
    List<Part> listByIds(List<Long> ids);

    /**
     * 更新零件检测结果
     *
     * @param partId           零件ID
     * @param isQualified      是否合格
     * @param inspectionDetails 检测详情
     * @param suggestion       检测建议
     * @return 是否成功
     */
    boolean updateInspectionResult(Long partId, Boolean isQualified, String inspectionDetails, String suggestion);

    IPage<PartInspectionVO> getPartInspectionPage(Page<PartInspectionVO> page, PartInspectionQuery wrapper);

    /**
     * 新增零件
     * <p>
     * 验证零件编码唯一性后保存零件信息
     * </p>
     *
     * @param dto 零件信息，包含零件名称、编码、检测标准等
     * @return 是否成功，true-成功，false-失败
     */
    boolean addPart(PartDTO dto);

    /**
     * 更新零件
     * <p>
     * 验证零件存在性和编码唯一性后更新零件信息
     * </p>
     *
     * @param dto 零件信息，ID必填
     * @return 是否成功，true-成功，false-失败
     */
    boolean updatePart(PartDTO dto);

    /**
     * 根据ID查询零件详情
     * <p>
     * 根据零件ID查询零件的完整信息
     * </p>
     *
     * @param id 零件ID
     * @return 零件详情，不存在时返回null
     */
    PartVO getPartDetailById(Long id);
}
