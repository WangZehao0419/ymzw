package com.ruoyi.equipment.entity.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 车间信息视图对象
 * <p>
 * 用于前端展示车间详情相关信息
 * </p>
 *
 * @author smartartisan
 */
@Data
public class WorkshopVO {

    /**
     * 主键ID
     */
    private Integer id;

    /**
     * 车间编号
     */
    private String workshopNo;

    /**
     * 车间名称
     */
    private String workshopName;

    /**
     * 车间位置
     */
    private String workshopLocation;

    /**
     * 车间负责人
     */
    private String workshopManager;

    /**
     * 车间状态（0-启用，1-停用）
     */
    private String workshopStatus;

    /**
     * 备注
     */
    private String workshopRemark;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 创建人
     */
    private String createUser;

    /**
     * 修改人
     */
    private String updateUser;
}
