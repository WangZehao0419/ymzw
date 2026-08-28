package com.ruoyi.inspection.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.inspection.entity.InspectionStandard;
import com.ruoyi.inspection.entity.Part;
import com.ruoyi.inspection.entity.query.PartInspectionQuery;
import com.ruoyi.inspection.entity.vo.InspectionResult;
import com.ruoyi.inspection.entity.vo.PartInspectionVO;

import java.util.List;
import java.util.Map;

/**
 * 零件检测 Service 接口
 */
public interface PartInspectionService {

    /**
     * 单个零件检测
     * @param partId 零件ID
     * @return 检测结果
     */
    InspectionResult inspectSinglePart(Long partId);

    /**
     * 批量零件检测
     * @param partIds 零件ID列表
     * @return 批量检测结果
     */
    Map<Long, InspectionResult> inspectBatchParts(List<Long> partIds);

    /**
     * 基于检测标准的零件检测
     * @param part 零件信息
     * @param standard 检测标准
     * @return 检测结果
     */
    InspectionResult inspectWithStandard(Part part, InspectionStandard standard);

    /**
     * 更新零件检测结果到数据库
     * @param partId 零件ID
     * @param result 检测结果
     */
    void updateInspectionResult(Long partId, InspectionResult result);

    /**
     * 根据ID获取零件信息
     * @param partId 零件ID
     * @return 零件信息
     */
    Part getPartById(Long partId);


    IPage<PartInspectionVO> page(PartInspectionQuery query);
}
