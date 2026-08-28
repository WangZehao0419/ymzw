package com.ruoyi.inspection.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.common.core.web.domain.AjaxResult;
import com.ruoyi.common.core.web.page.TableDataInfo;
import com.ruoyi.inspection.entity.InspectionStandard;
import com.ruoyi.inspection.entity.dto.InspectionStandardDTO;
import com.ruoyi.inspection.entity.query.InspectionStandardQuery;
import com.ruoyi.inspection.entity.vo.InspectionStandardVO;
import com.ruoyi.inspection.service.InspectionStandardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 检测标准管理 Controller
 * <p>
 * 提供检测标准的增删改查等接口
 * 数据源：数据库表 inspection_standards
 * </p>
 *
 * @author ruoyi
 */
@Slf4j
@RestController
@RequestMapping("/part/standard")
@RequiredArgsConstructor
public class InspectionStandardController {

    private final InspectionStandardService inspectionStandardService;

    @PostMapping
    public AjaxResult add(@Valid @RequestBody InspectionStandardDTO dto) {
        boolean success = inspectionStandardService.addStandard(dto);
        if (!success) {
            return AjaxResult.error("新增检测标准失败");
        }
        return AjaxResult.success();
    }

    @GetMapping("/list")
    public AjaxResult list(@RequestParam(value = "partType", required = false) String partType) {
        try {
            LambdaQueryWrapper<InspectionStandard> wrapper = new LambdaQueryWrapper<>();
            if (partType != null && !partType.isEmpty()) {
                wrapper.eq(InspectionStandard::getPartType, partType);
            }
            wrapper.orderByDesc(InspectionStandard::getUpdateTime);
            List<InspectionStandard> list = inspectionStandardService.list(wrapper);
            return AjaxResult.success(list);
        } catch (Exception e) {
            log.error("获取检测标准列表失败: {}", e.getMessage(), e);
            return AjaxResult.error("获取检测标准列表失败");
        }
    }

    @GetMapping("/{id}")
    public AjaxResult getById(@PathVariable Long id) {
        InspectionStandardVO vo = inspectionStandardService.getDetailById(id);
        if (vo == null) {
            return AjaxResult.error("检测标准不存在");
        }
        return AjaxResult.success(vo);
    }

    @GetMapping("/page")
    public TableDataInfo page(InspectionStandardQuery query) {
        IPage<InspectionStandardVO> page = inspectionStandardService.pageQuery(query);
        return new TableDataInfo(page.getRecords(), page.getTotal());
    }

    @PutMapping
    public AjaxResult update(@Valid @RequestBody InspectionStandardDTO dto) {
        if (dto.getId() == null) {
            return AjaxResult.error("检测标准ID不能为空");
        }
        boolean success = inspectionStandardService.updateStandard(dto);
        if (!success) {
            return AjaxResult.error("更新检测标准失败");
        }
        return AjaxResult.success();
    }

    @DeleteMapping("/{id}")
    public AjaxResult delete(@PathVariable Long id) {
        boolean success = inspectionStandardService.removeById(id);
        if (!success) {
            return AjaxResult.error("删除失败，检测标准不存在");
        }
        return AjaxResult.success();
    }
}
