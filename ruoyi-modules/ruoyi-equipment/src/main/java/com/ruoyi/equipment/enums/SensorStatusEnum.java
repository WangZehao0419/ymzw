package com.ruoyi.equipment.enums;

import lombok.Getter;

/**
 * 传感器状态枚举类
 * <p>
 * 定义传感器的启用/禁用状态
 * </p>
 *
 * @author smartartisan
 */
@Getter
public enum SensorStatusEnum {

    DISABLED(0, "禁用"),
    ENABLED(1, "启用");

    private final Integer code;
    private final String desc;

    SensorStatusEnum(Integer code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据状态码获取对应的枚举实例
     *
     * @param code 状态码
     * @return 对应的枚举实例，如果不存在则返回null
     */
    public static SensorStatusEnum getByCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (SensorStatusEnum status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        return null;
    }
}
