package com.ruoyi.equipment.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.common.core.web.domain.AjaxResult;
import com.ruoyi.common.core.web.page.TableDataInfo;
import com.ruoyi.equipment.entity.Workshop;
import com.ruoyi.equipment.entity.dto.EquipmentLayoutDTO;
import com.ruoyi.equipment.entity.dto.WorkshopDTO;
import com.ruoyi.equipment.entity.query.WorkshopQuery;
import com.ruoyi.equipment.entity.vo.WorkshopVO;
import com.ruoyi.equipment.service.WorkshopService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 车间基础信息 Controller
 * <p>
 * 提供车间信息的增删改查等接口
 * </p>
 *
 * @author smartartisan
 */
@RestController
@RequestMapping("/equipment/workshop")
@RequiredArgsConstructor
public class WorkshopController {

    private final WorkshopService workshopService;

    /**
     * 分页查询车间列表
     *
     * @param query 查询参数
     * @return 分页结果
     */
    @GetMapping("/page")
    public TableDataInfo page(WorkshopQuery query) {
        IPage<WorkshopVO> page = workshopService.page(query);
        return new TableDataInfo(page.getRecords(), page.getTotal());
    }

    /**
     * 根据ID查询车间详情
     *
     * @param id 车间ID
     * @return 车间详情
     */
    @GetMapping("/{id}")
    public AjaxResult getById(@PathVariable Integer id) {
        WorkshopVO vo = workshopService.getDetailById(id);
        if (vo == null) {
            return AjaxResult.error("车间不存在");
        }
        return AjaxResult.success(vo);
    }

    /**
     * 新增车间
     *
     * @param dto 车间信息
     * @return 操作结果
     */
    @PostMapping
    public AjaxResult add(@Valid @RequestBody WorkshopDTO dto) {
        Workshop workshop = new Workshop();
        BeanUtils.copyProperties(dto, workshop);
        workshopService.addWorkshop(workshop);
        return AjaxResult.success();
    }

    /**
     * 更新车间
     *
     * @param dto 车间信息
     * @return 操作结果
     */
    @PutMapping
    public AjaxResult update(@Valid @RequestBody WorkshopDTO dto) {
        if (dto.getId() == null) {
            return AjaxResult.error("车间ID不能为空");
        }
        Workshop workshop = new Workshop();
        BeanUtils.copyProperties(dto, workshop);
        workshopService.updateWorkshop(workshop);
        return AjaxResult.success();
    }

    /**
     * 删除车间（逻辑删除，车间下存在设备时禁止删除）
     *
     * @param id 车间ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public AjaxResult delete(@PathVariable Integer id) {
        boolean success = workshopService.removeWorkshop(id);
        if (!success) {
            return AjaxResult.error("删除失败，车间不存在");
        }
        return AjaxResult.success();
    }

    /**
     * 批量保存车间设备孪生布局
     *
     * @param workshopId 车间ID
     * @param layouts    设备布局列表
     * @return 操作结果
     */
    @PutMapping("/{workshopId}/layout")
    public AjaxResult saveLayout(@PathVariable Integer workshopId, @RequestBody List<EquipmentLayoutDTO> layouts) {
        workshopService.saveLayout(workshopId, layouts);
        return AjaxResult.success();
    }
}
