package com.ruoyi.inspection.service;

import com.baomidou.mybatisplus.extension.repository.IRepository;
import com.ruoyi.inspection.entity.InspectionRecord;

import java.util.List;

/**
 * 检测记录 Service 接口
 * <p>
 * 继承 MyBatis-Plus 的 IService，自动拥有 CRUD 功能
 * </p>
 *
 * @author smartartisan
 */
public interface InspectionRecordService extends IRepository<InspectionRecord> {

    /**
     * 根据零件ID查询检测记录列表
     *
     * @param partId 零件ID
     * @return 检测记录列表
     */
    List<InspectionRecord> listByPartId(Long partId);
}
