package com.ruoyi.inspection.entity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 零件信息数据传输对象
 * <p>
 * 用于接收前端提交的零件新增和更新请求参数
 * </p>
 *
 * @author smartartisan
 */
@Data
public class PartDTO {

    /**
     * 主键ID（更新时必填）
     */
    private Long id;

    /**
     * 零件名称
     */
    @NotBlank(message = "零件名称不能为空")
    private String partName;

    /**
     * 零件编码
     */
    @NotBlank(message = "零件编码不能为空")
    private String partCode;

    /**
     * 检测标准ID
     */
    @NotNull(message = "检测标准ID不能为空")
    private Long standardId;

    /**
     * 零件参数（JSON格式）
     */
    private String parameters;

    /**
     * 是否合格（true-合格，false-不合格）
     */
    private Boolean isQualified;

    /**
     * 检测标志（true-已检测，false-未检测）
     */
    private Boolean inspectionFlag;

    /**
     * 检测详情
     */
    private String inspectionDetails;

    /**
     * 检测建议
     */
    private String inspectionSuggestion;
}
