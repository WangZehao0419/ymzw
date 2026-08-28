package com.ruoyi.equipment.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.common.core.web.domain.AjaxResult;
import com.ruoyi.common.core.web.page.TableDataInfo;
import com.ruoyi.equipment.entity.EquipmentSensor;
import com.ruoyi.equipment.entity.dto.EquipmentSensorDTO;
import com.ruoyi.equipment.entity.query.EquipmentSensorQuery;
import com.ruoyi.equipment.entity.vo.EquipmentSensorVO;
import com.ruoyi.equipment.service.AlertAsyncService;
import com.ruoyi.equipment.service.EquipmentSensorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 设备传感器 Controller
 * <p>
 * 提供传感器信息的增删改查等接口
 * </p>
 *
 * @author smartartisan
 */
@Slf4j
@RestController
@RequestMapping("/equipment/sensor")
@RequiredArgsConstructor
public class EquipmentSensorController {

    private final EquipmentSensorService equipmentSensorService;
    private final AlertAsyncService alertAsyncService;

    /**
     * 分页查询传感器列表
     *
     * @param query 查询参数
     * @return 分页结果
     */
    @GetMapping("/page")
    public TableDataInfo page(EquipmentSensorQuery query) {
        IPage<EquipmentSensorVO> page = equipmentSensorService.page(query);
        return new TableDataInfo(page.getRecords(), page.getTotal());
    }

    /**
     * 根据ID查询传感器详情
     *
     * @param id 传感器ID
     * @return 传感器详情
     */
    @GetMapping("/{id}")
    public AjaxResult getById(@PathVariable Integer id) {
        EquipmentSensorVO vo = equipmentSensorService.getDetailById(id);
        if (vo == null) {
            return AjaxResult.error("传感器不存在");
        }
        return AjaxResult.success(vo);
    }

    /**
     * 查询指定设备的传感器列表
     *
     * @param equipmentId 设备ID
     * @return 传感器列表
     */
    @GetMapping("/equipment/{equipmentId}")
    public AjaxResult getByEquipmentId(@PathVariable Integer equipmentId) {
        List<EquipmentSensorVO> list = equipmentSensorService.getByEquipmentId(equipmentId);

        // 异步调用：不阻塞主线程，在后台独立执行
        alertAsyncService.sendAlert(equipmentId, LocalDateTime.now());
        log.info("主线程返回，异步告警已在后台执行");

        return AjaxResult.success(list);
    }

    /**
     * 新增传感器
     *
     * @param dto 传感器信息
     * @return 操作结果
     */
    @PostMapping
    public AjaxResult add(@Valid @RequestBody EquipmentSensorDTO dto) {
        EquipmentSensor sensor = new EquipmentSensor();
        BeanUtils.copyProperties(dto, sensor);
        equipmentSensorService.addSensor(sensor);
        return AjaxResult.success();
    }

    /**
     * 更新传感器
     *
     * @param dto 传感器信息
     * @return 操作结果
     */
    @PutMapping
    public AjaxResult update(@Valid @RequestBody EquipmentSensorDTO dto) {
        if (dto.getId() == null) {
            return AjaxResult.error("传感器ID不能为空");
        }
        EquipmentSensor sensor = new EquipmentSensor();
        BeanUtils.copyProperties(dto, sensor);
        equipmentSensorService.updateSensor(sensor);
        return AjaxResult.success();
    }

    /**
     * 删除传感器（逻辑删除）
     *
     * @param id 传感器ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public AjaxResult delete(@PathVariable Integer id) {
        boolean success = equipmentSensorService.removeById(id);
        if (!success) {
            return AjaxResult.error("删除失败，传感器不存在");
        }
        return AjaxResult.success();
    }

    /**
     * 启用/禁用传感器
     *
     * @param id     传感器ID
     * @param status 传感器状态（0-禁用，1-启用）
     * @return 操作结果
     */
    @PutMapping("/status/{id}")
    public AjaxResult updateStatus(@PathVariable Integer id, @RequestParam Integer status) {
        equipmentSensorService.updateStatus(id, status);
        return AjaxResult.success();
    }
}
