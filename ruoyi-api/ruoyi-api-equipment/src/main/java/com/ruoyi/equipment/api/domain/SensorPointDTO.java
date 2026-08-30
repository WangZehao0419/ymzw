package com.ruoyi.equipment.api.domain;

import lombok.Data;

import java.io.Serializable;

/**
 * 传感器时序数据点传输对象（历史窗口跨服务查询）
 * <p>
 * ts 用 Long(epoch millis) 而非 LocalDateTime:LocalDateTime 跨服务经 Jackson
 * 序列化时,若任一侧未注册 JavaTimeModule,会退化为 [2026,8,30,10,30,0] 形式的
 * 数组,反序列化易踩坑,且时区语义随两侧系统默认时区漂移;epoch millis 是
 * 无时区歧义的纯数值,Feign 两侧与前端 new Date(ms) 行为完全一致。
 * </p>
 *
 * @author smartartisan
 */
@Data
public class SensorPointDTO implements Serializable
{
    private static final long serialVersionUID = 1L;

    /**
     * 采集时间（epoch 毫秒）
     */
    private Long ts;

    /**
     * 采集数值
     */
    private Double val;
}
