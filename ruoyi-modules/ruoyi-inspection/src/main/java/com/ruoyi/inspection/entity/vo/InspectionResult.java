package com.ruoyi.inspection.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 检测结果VO
 * <p>
 * 用于封装AI智能检测的返回结果
 * </p>
 *
 * @author smartartisan
 */
@Data
@AllArgsConstructor
public class InspectionResult {

    /**
     * 是否通过检测
     */
    private boolean passed;

    /**
     * 检测详情
     */
    private String details;

    /**
     * 检测建议
     */
    private String suggestion;

    /**
     * 创建失败的检测结果
     *
     * @param message 失败信息
     * @return 检测结果
     */
    public static InspectionResult fail(String message) {
        return new InspectionResult(false, message, "");
    }
}
