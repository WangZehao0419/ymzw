package com.ruoyi.inspection.controller;

import com.ruoyi.common.core.web.domain.AjaxResult;
import com.ruoyi.common.core.web.page.TableDataInfo;
import com.ruoyi.inspection.entity.dto.PartDTO;
import com.ruoyi.inspection.entity.vo.PartVO;
import com.ruoyi.inspection.service.PartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 零件管理 Controller
 * <p>
 * 提供零件信息的增删改查等接口
 * </p>
 *
 * @author smartartisan
 */
@RestController
@RequestMapping("/parts")
@RequiredArgsConstructor
public class PartController {

    private final PartService partService;

    /**
     * 新增零件
     *
     * @param dto 零件信息
     * @return 操作结果
     */
    @PostMapping
    public AjaxResult add(@Valid @RequestBody PartDTO dto) {
        partService.addPart(dto);
        return AjaxResult.success();
    }

    /**
     * 根据ID查询零件详情
     *
     * @param id 零件ID
     * @return 零件详情
     */
    @GetMapping("/{id}")
    public AjaxResult getById(@PathVariable Long id) {
        PartVO vo = partService.getPartDetailById(id);
        if (vo == null) {
            return AjaxResult.error("零件不存在");
        }
        return AjaxResult.success(vo);
    }

    /**
     * 更新零件
     *
     * @param dto 零件信息
     * @return 操作结果
     */
    @PutMapping
    public AjaxResult update(@Valid @RequestBody PartDTO dto) {
        partService.updatePart(dto);
        return AjaxResult.success();
    }

    /**
     * 删除零件（逻辑删除）
     *
     * @param id 零件ID
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    public AjaxResult delete(@PathVariable Long id) {
        boolean success = partService.removeById(id);
        if (!success) {
            return AjaxResult.error("删除失败,零件不存在");
        }
        return AjaxResult.success();
    }
}
