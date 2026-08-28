package com.ruoyi.inspection.entity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 检测标准数据传输对象
 * <p>
 * 用于接收前端提交的检测标准新增和更新请求参数
 * </p>
 *
 * @author smartartisan
 */
@Data
public class InspectionStandardDTO {

    /**
     * 主键ID（更新时必填）
     */
    private Long id;

    /**
     * 零件类型
     */
    @NotBlank(message = "零件类型不能为空")
    private String partType;

    /**
     * 标准名称
     */
    @NotBlank(message = "标准名称不能为空")
    private String standardName;

    /**
     * 标准参数（JSON格式）
     */
    @NotBlank(message = "标准参数不能为空")
    private String standardParameters;

    /**
     * 描述
     */
    private String description;
}
