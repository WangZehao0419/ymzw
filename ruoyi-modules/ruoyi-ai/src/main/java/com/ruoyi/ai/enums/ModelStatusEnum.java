package com.ruoyi.ai.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * AI模型状态枚举
 * <p>
 * 定义模型的启用/禁用状态
 * </p>
 *
 * @author smartartisan
 */
@Getter
@AllArgsConstructor
public enum ModelStatusEnum {

    /**
     * 禁用状态
     */
    DISABLED(0, "禁用"),

    /**
     * 启用状态
     */
    ENABLED(1, "启用");

    /**
     * 状态编码
     */
    private final Integer code;

    /**
     * 状态描述
     */
    private final String description;

    /**
     * 根据编码获取枚举
     *
     * @param code 状态编码
     * @return 枚举对象，未找到返回null
     */
    public static ModelStatusEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (ModelStatusEnum status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 校验状态编码是否有效
     *
     * @param code 状态编码
     * @return true-有效，false-无效
     */
    public static boolean isValidCode(Integer code) {
        return getByCode(code) != null;
    }
}
