package com.ruoyi.inspection.service;

import com.ruoyi.inspection.entity.dto.PartInspectionDTO;
import com.ruoyi.inspection.entity.vo.PartInspectionResultVO;

/**
 * AI检测服务
 */
public interface AiInspectionService {

    /**
     * 进行零件AI检测
     *
     * @param request 检测请求
     * @return 检测结果
     */
    PartInspectionResultVO inspect(PartInspectionDTO request);
}
