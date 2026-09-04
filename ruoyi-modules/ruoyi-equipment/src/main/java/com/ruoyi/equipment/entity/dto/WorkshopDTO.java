package com.ruoyi.equipment.entity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 车间信息数据传输对象
 * <p>
 * 用于接收前端提交的车间新增和更新请求参数
 * </p>
 *
 * @author smartartisan
 */
@Data
public class WorkshopDTO {

    /**
     * 主键ID（更新时必填）
     */
    private Integer id;

    /**
     * 车间名称
     */
    @NotBlank(message = "车间名称不能为空")
    private String workshopName;

    /**
     * 车间位置
     */
    private String workshopLocation;

    /**
     * 车间负责人用户ID（逻辑关联 sys_user.user_id）
     */
    private Integer workshopManagerId;

    /**
     * 车间负责人姓名（冗余显示，随用户选择覆盖）
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
}
