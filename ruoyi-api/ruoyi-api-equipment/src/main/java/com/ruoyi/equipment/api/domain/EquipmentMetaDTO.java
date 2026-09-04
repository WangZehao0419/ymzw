package com.ruoyi.equipment.api.domain;

import lombok.Data;

/**
 * 设备元数据传输对象，用于跨模块元数据传输，只含告警侧必需字段
 *
 * @author smartartisan
 */
@Data
public class EquipmentMetaDTO
{
    /**
     * 设备ID
     */
    private Integer id;

    /**
     * 设备名称
     */
    private String equipmentName;

    /**
     * 设备归属用户ID
     */
    private Integer equipmentUserId;

    /**
     * 设备负责人姓名
     */
    private String equipmentUserName;
}
