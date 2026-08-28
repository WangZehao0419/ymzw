package com.ruoyi.inspection.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.inspection.entity.Part;
import com.ruoyi.inspection.entity.query.PartInspectionQuery;
import com.ruoyi.inspection.entity.query.PartQuery;
import com.ruoyi.inspection.entity.vo.PartInspectionVO;
import com.ruoyi.inspection.entity.vo.PartVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 零件 Mapper 接口
 * <p>
 * 继承 MyBatis-Plus 的 BaseMapper，自动拥有 CRUD 功能
 * 提供零件信息的数据库操作方法
 * </p>
 *
 * @author smartartisan
 */
@Mapper
public interface PartMapper extends BaseMapper<Part> {

    /**
     * 更新零件检测结果
     * <p>
     * 根据零件ID更新检测相关字段，包括合格状态、检测时间、检测详情和建议
     * </p>
     *
     * @param partId              零件ID，必填
     * @param isQualified         是否合格，true-合格，false-不合格
     * @param inspectionDetails   检测详情，JSON格式的检测结果
     * @param suggestion          检测建议，针对检测结果的改进建议
     * @return 影响行数，成功返回1，失败返回0
     */
    int updateInspectionResult(@Param("partId") Long partId,
                               @Param("isQualified") Boolean isQualified,
                               @Param("inspectionDetails") String inspectionDetails,
                               @Param("inspectionSuggestion") String suggestion);

    /**
     * 分页查询零件检测信息
     * <p>
     * 用于检测管理页面，查询零件的检测状态和结果信息
     * 支持按零件名称和检测标志进行筛选
     * </p>
     *
     * @param page  分页参数，包含页码和每页条数
     * @param query 查询条件，包含零件名称和检测标志
     * @return 分页结果，包含PartInspectionVO列表
     */
    IPage<PartInspectionVO> selectPartInspectionPage(Page<PartInspectionVO> page, PartInspectionQuery query);
}
