package com.ruoyi.ai.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.repository.IRepository;
import com.ruoyi.ai.entity.DiagnosisRecord;
import com.ruoyi.ai.entity.query.DiagnosisRecordQuery;

/**
 * AI诊断报告服务接口
 * <p>
 * 提供诊断报告的落库与历史查询能力，
 * 诊断链路（DiagnosisService）生成报告后调用本服务持久化
 * </p>
 *
 * @author smartartisan
 */
public interface DiagnosisRecordService extends IRepository<DiagnosisRecord> {

    /**
     * 保存诊断报告
     *
     * @param equipmentId 设备ID
     * @param sensorCode  传感器编号
     * @param report      诊断报告（Markdown 全文）
     * @return 落库后的记录（含自增ID与创建时间）
     */
    DiagnosisRecord saveReport(Integer equipmentId, String sensorCode, String report);

    /**
     * 分页查询诊断报告历史
     *
     * @param query 查询参数（equipmentId/sensorCode 可选过滤）
     * @return 分页结果
     */
    IPage<DiagnosisRecord> page(DiagnosisRecordQuery query);
}
