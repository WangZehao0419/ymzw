package com.ruoyi.equipment.entity.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 车间设备孪生布局数据传输对象
 * <p>
 * 用于接收前端批量保存车间设备孪生布局的请求参数。
 * 与 EquipmentDTO 分离，保持设备属性编辑接口与布局接口职责隔离
 * </p>
 *
 * @author smartartisan
 */
@Data
public class EquipmentLayoutDTO {

    /**
     * 设备ID（定位需要更新布局的设备）
     */
    @NotNull(message = "设备ID不能为空")
    private Integer id;

    /**
     * 孪生布局X(米,地面世界坐标,中心原点,NULL表示移回清单)
     */
    private Double layoutX;

    /**
     * 孪生布局Y(米,地面世界坐标,中心原点,NULL表示移回清单)
     */
    private Double layoutY;
}
