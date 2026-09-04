package com.ruoyi.equipment.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.core.utils.StringUtils;
import com.ruoyi.equipment.entity.Equipment;
import com.ruoyi.equipment.entity.Workshop;
import com.ruoyi.equipment.entity.dto.EquipmentLayoutDTO;
import com.ruoyi.equipment.entity.query.WorkshopQuery;
import com.ruoyi.equipment.entity.vo.WorkshopVO;
import com.ruoyi.equipment.mapper.EquipmentMapper;
import com.ruoyi.equipment.mapper.WorkshopMapper;
import com.ruoyi.equipment.repository.BaseRepository;
import com.ruoyi.equipment.service.WorkshopService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 车间基础信息 Service 实现类
 * <p>
 * 使用 MyBatis-Plus 的 Service 层进行数据操作
 * </p>
 *
 * @author smartartisan
 */
@Service
@RequiredArgsConstructor
public class WorkshopServiceImpl extends BaseRepository<WorkshopMapper, Workshop> implements WorkshopService {

    private final WorkshopMapper workshopMapper;

    /**
     * 注入设备 Mapper 仅为删除保护服务：车间被删后其下设备将失去归属，
     * 故删除车间前必须确认其下无在册设备
     */
    private final EquipmentMapper equipmentMapper;

    @Override
    public WorkshopMapper getBaseMapper() {
        return workshopMapper;
    }

    @Override
    public IPage<WorkshopVO> page(WorkshopQuery query) {
        // 状态是明确值用精确匹配，名称需支持模糊搜索；条件式写法避免拼空的 where
        LambdaQueryWrapper<Workshop> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.isNotEmpty(query.getWorkshopName()), Workshop::getWorkshopName, query.getWorkshopName())
                .eq(StringUtils.isNotEmpty(query.getWorkshopStatus()), Workshop::getWorkshopStatus, query.getWorkshopStatus())
                .orderByDesc(Workshop::getId);
        Page<Workshop> page = new Page<>(query.getPage(), query.getPageSize());
        return this.page(page, wrapper).convert(workshop -> {
            WorkshopVO vo = new WorkshopVO();
            BeanUtils.copyProperties(workshop, vo);
            return vo;
        });
    }

    @Override
    public WorkshopVO getDetailById(Integer id) {
        Workshop workshop = this.getById(id);
        if (workshop == null) {
            return null;
        }
        WorkshopVO vo = new WorkshopVO();
        BeanUtils.copyProperties(workshop, vo);
        return vo;
    }

    @Override
    public boolean addWorkshop(Workshop workshop) {
        return this.save(workshop);
    }

    @Override
    public boolean updateWorkshop(Workshop workshop) {
        Workshop existingWorkshop = this.getById(workshop.getId());
        if (existingWorkshop == null) {
            throw new ServiceException("车间不存在");
        }
        return this.updateById(workshop);
    }

    @Override
    public boolean removeWorkshop(Integer id) {
        // 删除保护：车间下仍挂有设备时禁止删除，避免设备数据失去车间归属。
        // Equipment 的 deleteFlag 带 @TableLogic，MP 会自动过滤已删设备，因此这里不能再手写 delete_flag 条件
        LambdaQueryWrapper<Equipment> equipmentWrapper = new LambdaQueryWrapper<>();
        equipmentWrapper.eq(Equipment::getWorkshopId, id);
        Long equipmentCount = equipmentMapper.selectCount(equipmentWrapper);
        if (equipmentCount != null && equipmentCount > 0) {
            throw new ServiceException("车间下存在设备，无法删除");
        }
        return this.removeById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveLayout(Integer workshopId, List<EquipmentLayoutDTO> layouts) {
        // 空列表视为无任何变更，直接成功，避免无意义的空事务开销
        // 事务保证：中途某条校验失败（越权/不存在）时已执行的更新回滚，实现 spec 的"整体拒绝"语义
        if (layouts == null || layouts.isEmpty()) {
            return true;
        }
        for (EquipmentLayoutDTO item : layouts) {
            // 先校验设备存在且归属当前车间，防止误提交其他车间设备ID导致越权改写布局
            LambdaQueryWrapper<Equipment> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(Equipment::getId, item.getId());
            Equipment equipment = equipmentMapper.selectOne(queryWrapper);
            if (equipment == null) {
                throw new ServiceException("设备不存在");
            }
            if (!workshopId.equals(equipment.getWorkshopId())) {
                throw new ServiceException("设备不属于当前车间");
            }
            // 关键设计：必须用 UpdateWrapper 的 set 显式写入而非 updateById——
            // updateById 默认策略会忽略 null 字段，"移回清单"（清空坐标）将无法落库
            LambdaUpdateWrapper<Equipment> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(Equipment::getId, item.getId())
                    .set(Equipment::getLayoutX, item.getLayoutX())
                    .set(Equipment::getLayoutY, item.getLayoutY());
            equipmentMapper.update(null, updateWrapper);
        }
        return true;
    }
}
