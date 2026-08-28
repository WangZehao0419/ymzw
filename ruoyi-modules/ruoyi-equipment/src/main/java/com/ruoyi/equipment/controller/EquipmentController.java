package com.ruoyi.equipment.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.common.core.web.domain.AjaxResult;
import com.ruoyi.common.core.web.page.TableDataInfo;
import com.ruoyi.equipment.entity.Equipment;
import com.ruoyi.equipment.entity.dto.EquipmentDTO;
import com.ruoyi.equipment.entity.query.EquipmentQuery;
import com.ruoyi.equipment.entity.vo.EquipmentVO;
import com.ruoyi.equipment.service.EquipmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

/**
 * 设备基础信息 Controller
 * <p>
 * 提供设备信息的增删改查等接口
 * </p>
 *
 * @author smartartisan
 */
@RestController
@RequestMapping("/equipment")
@RequiredArgsConstructor
public class EquipmentController {

    private final EquipmentService equipmentService;

    /**
     * 分页查询设备列表
     *
     * @param query 查询参数
     * @return 分页结果
     */
    @GetMapping("/page")
    public TableDataInfo page(EquipmentQuery query) {
        IPage<EquipmentVO> page = equipmentService.page(query);
        return new TableDataInfo(page.getRecords(), page.getTotal());
    }

    /**
     * 根据ID查询设备详情
     *
     * @param id 设备ID
     * @return 设备详情
     */
    @GetMapping("/{id}")
    public AjaxResult getById(@PathVariable Integer id) {
        EquipmentVO vo = equipmentService.getDetailById(id);
        if (vo == null) {
            return AjaxResult.error("设备不存在");
        }
        return AjaxResult.success(vo);
    }

    /**
     * 新增设备
     *
     * @param dto 设备信息
     * @return 操作结果
     */
    @PostMapping
    public AjaxResult add(@Valid @RequestBody EquipmentDTO dto) {
        Equipment equipment = new Equipment();
        BeanUtils.copyProperties(dto, equipment);
        equipmentService.addEquipment(equipment);
        return AjaxResult.success();
    }

    /**
     * 更新设备
     *
     * @param dto 设备信息
     * @return 操作结果
     */
    @PutMapping
    public AjaxResult update(@Valid @RequestBody EquipmentDTO dto) {
        if (dto.getId() == null) {
            return AjaxResult.error("设备ID不能为空");
        }
        Equipment equipment = new Equipment();
        BeanUtils.copyProperties(dto, equipment);
        equipmentService.updateEquipment(equipment);
        return AjaxResult.success();
    }

    /**
     * 删除设备（逻辑删除）
     *
     * @param id 设备ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public AjaxResult delete(@PathVariable Integer id) {
        boolean success = equipmentService.removeById(id);
        if (!success) {
            return AjaxResult.error("删除失败，设备不存在");
        }
        return AjaxResult.success();
    }

    /**
     * 更新设备状态
     *
     * @param id     设备ID
     * @param status 设备状态
     * @return 操作结果
     */
    @PutMapping("/status/{id}")
    public AjaxResult updateStatus(@PathVariable Integer id, @RequestParam String status) {
        equipmentService.updateStatus(id, status);
        return AjaxResult.success();
    }
}
