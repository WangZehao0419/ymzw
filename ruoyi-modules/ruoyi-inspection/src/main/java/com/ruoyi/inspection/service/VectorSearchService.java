package com.ruoyi.inspection.service;

/**
 * 向量库检索服务
 */
public interface VectorSearchService {

    /**
     * 判断向量库中是否存在该零件类型的检测标准
     *
     * @param partType 零件类型
     * @return true-存在，false-不存在
     */
    boolean existsInspectionMethodology(String partType);
}
