package com.ruoyi.ai.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 诊断报告结果视图对象
 * <p>
 * 诊断接口的响应体：LLM 生成的 Markdown 报告 + 落库记录 ID + RAG 知识引用来源
 * </p>
 *
 * @author smartartisan
 */
@Data
@Schema(description = "诊断报告结果")
public class DiagnosisResultVO {

    /**
     * 诊断智能体ID（未配置诊断智能体、使用默认模型兜底时为 null）
     */
    @Schema(description = "诊断智能体ID（默认模型兜底时为空）")
    private Long agentId;

    /**
     * 诊断智能体名称（默认模型兜底时为"默认模型"）
     */
    @Schema(description = "诊断智能体名称")
    private String agentName;

    /**
     * 是否使用默认模型兜底
     */
    @Schema(description = "是否使用默认模型兜底（未配置诊断智能体时 true）")
    private Boolean defaultModel;

    /**
     * 设备ID
     */
    @Schema(description = "设备ID")
    private Integer equipmentId;

    /**
     * 传感器编号
     */
    @Schema(description = "传感器编号")
    private String sensorCode;

    /**
     * 预测状态（NORMAL/DEGRADING/BREACHED）
     */
    @Schema(description = "预测状态")
    private String predictStatus;

    /**
     * 诊断报告（Markdown 全文）
     */
    @Schema(description = "诊断报告(Markdown)")
    private String report;

    /**
     * 诊断记录ID（diagnosis_record 落库主键）
     */
    @Schema(description = "诊断记录ID")
    private Long recordId;

    /**
     * RAG 引用的知识条目数
     */
    @Schema(description = "RAG引用知识条目数")
    private Integer sourceCount;

    /**
     * RAG 引用的知识条目标题列表（报告"知识来源"章节的可追溯依据）
     */
    @Schema(description = "RAG引用知识条目标题列表")
    private List<String> sources;
}
