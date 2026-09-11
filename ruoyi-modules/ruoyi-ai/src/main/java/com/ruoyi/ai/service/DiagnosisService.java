package com.ruoyi.ai.service;

import com.ruoyi.ai.entity.vo.DiagnosisResultVO;

/**
 * 设备诊断服务接口（诊断智能体核心链路）
 * <p>
 * 诊断流程：拉取预测数据（工具①）→ Qdrant RAG 知识检索（工具②）→ LLM 生成报告 → 落库。
 * 诊断失败（无预测数据 / Qdrant 不可用 / LLM 调用失败）直接抛异常，
 * 由 Controller 统一转为错误响应——按用户决策不做降级。
 * </p>
 *
 * @author smartartisan
 */
public interface DiagnosisService {

    /**
     * 对指定设备的传感器执行 AI 诊断
     *
     * @param equipmentId 设备ID
     * @param sensorCode  传感器编号（如 TH-001）
     * @return 诊断结果（报告 + 落库记录ID + 知识引用）
     */
    DiagnosisResultVO diagnose(Integer equipmentId, String sensorCode);
}
