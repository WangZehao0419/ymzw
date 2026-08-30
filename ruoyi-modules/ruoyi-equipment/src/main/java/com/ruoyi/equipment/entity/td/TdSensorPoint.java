package com.ruoyi.equipment.entity.td;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * TDengine 时序查询结果行: sensor_id + ts + val
 * <p>
 * 独立于业务实体:时序库只存 id 与数值,名称/单位等元数据由调用方
 * 回查 MySQL 传感器表补充,避免把元数据冗余进时序库。
 * </p>
 *
 * @author smartartisan
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TdSensorPoint {

    /**
     * 传感器 ID(超级表 tag,与 MySQL 传感器主键同源)
     */
    private Integer sensorId;

    /**
     * 采集时间
     */
    private LocalDateTime ts;

    /**
     * 采集数值
     */
    private Double val;
}
