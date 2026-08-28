package com.ruoyi.equipment.enums;

import lombok.Getter;

/**
 * 设备状态枚举类
 * <p>
 * 定义设备的状态类型，包括运行中、待机、维护中、离线
 * </p>
 *
 * @author smartartisan
 */
@Getter
public enum EquipmentStatusEnum {

    RUNNING("0", "运行中"),
    STANDBY("1", "待机"),
    MAINTENANCE("2", "维护中"),
    OFFLINE("3", "离线");

    private final String code;
    private final String desc;

    EquipmentStatusEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据状态码获取对应的枚举实例
     *
     * @param code 状态码
     * @return 对应的枚举实例，如果不存在则返回null
     */
    public static EquipmentStatusEnum getByCode(String code) {
        if (code == null) {
            return null;
        }
        for (EquipmentStatusEnum status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        return null;
    }
}
