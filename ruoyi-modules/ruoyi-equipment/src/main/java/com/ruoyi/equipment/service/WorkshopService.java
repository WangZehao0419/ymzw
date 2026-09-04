package com.ruoyi.equipment.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.repository.IRepository;
import com.ruoyi.equipment.entity.Workshop;
import com.ruoyi.equipment.entity.dto.EquipmentLayoutDTO;
import com.ruoyi.equipment.entity.query.WorkshopQuery;
import com.ruoyi.equipment.entity.vo.WorkshopVO;

import java.util.List;

/**
 * 车间基础信息 Service 接口
 * <p>
 * 提供车间信息的增删改查等业务操作
 * </p>
 *
 * @author smartartisan
 */
public interface WorkshopService extends IRepository<Workshop> {

    /**
     * 分页查询车间列表
     *
     * @param query 查询参数
     * @return 分页结果
     */
    IPage<WorkshopVO> page(WorkshopQuery query);

    /**
     * 根据ID查询车间详情
     *
     * @param id 车间ID
     * @return 车间详情
     */
    WorkshopVO getDetailById(Integer id);

    /**
     * 新增车间
     *
     * @param workshop 车间信息
     * @return 是否成功
     */
    boolean addWorkshop(Workshop workshop);

    /**
     * 更新车间
     *
     * @param workshop 车间信息
     * @return 是否成功
     */
    boolean updateWorkshop(Workshop workshop);

    /**
     * 删除车间（删除前校验车间下是否存在设备）
     *
     * @param id 车间ID
     * @return 是否成功
     */
    boolean removeWorkshop(Integer id);

    /**
     * 批量保存车间设备孪生布局
     *
     * @param workshopId 车间ID
     * @param layouts    设备布局列表（layoutX/layoutY 为 null 表示移回清单）
     */
    boolean saveLayout(Integer workshopId, List<EquipmentLayoutDTO> layouts);
}
