package com.ruoyi.inspection.entity.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 检测标准视图对象
 * <p>
 * 用于前端展示检测标准详情相关信息
 * </p>
 *
 * @author smartartisan
 */
@Data
public class InspectionStandardVO {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 零件类型
     */
    private String partType;

    /**
     * 标准名称
     */
    private String standardName;

    /**
     * 标准参数（JSON格式）
     */
    private String standardParameters;

    /**
     * 描述
     */
    private String description;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
