package com.ruoyi.inspection.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.common.core.web.domain.AjaxResult;
import com.ruoyi.common.core.web.page.TableDataInfo;
import com.ruoyi.inspection.entity.Part;
import com.ruoyi.inspection.entity.dto.PartInspectionDTO;
import com.ruoyi.inspection.entity.query.PartInspectionQuery;
import com.ruoyi.inspection.entity.vo.InspectionResult;
import com.ruoyi.inspection.entity.vo.PartInspectionResultVO;
import com.ruoyi.inspection.entity.vo.PartInspectionVO;
import com.ruoyi.inspection.service.AiInspectionService;
import com.ruoyi.inspection.service.PartInspectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 零件检测 Controller
 * <p>
 * 提供零件AI智能检测相关接口
 * </p>
 *
 * @author smartartisan
 */
@RestController
@RequestMapping("/part/inspection")
@RequiredArgsConstructor
public class PartInspectionController {

    private final PartInspectionService partInspectionService;
    private final AiInspectionService aiInspectionService;

    @GetMapping("/page")
    public ResponseEntity<IPage<PartInspectionVO>> page(PartInspectionQuery query) {
        IPage<PartInspectionVO> page = partInspectionService.page(query);
        return ResponseEntity.ok(page);
    }

    /**
     * 单个零件检测
     *
     * @param partId 零件ID
     * @return 检测结果
     */
    @GetMapping("/single/{partId}")
    public ResponseEntity<InspectionResult> inspectSinglePart(@PathVariable Long partId) {
        InspectionResult result = partInspectionService.inspectSinglePart(partId);
        return ResponseEntity.ok(result);
    }

    /**
     * 批量零件检测
     *
     * @param partIds 零件ID列表
     * @return 批量检测结果
     */
    @PostMapping("/batch")
    public ResponseEntity<Map<Long, InspectionResult>> inspectBatchParts(@RequestBody List<Long> partIds) {
        Map<Long, InspectionResult> results = partInspectionService.inspectBatchParts(partIds);
        return ResponseEntity.ok(results);
    }

    /**
     * AI智能检测
     *
     * @param request 检测请求
     * @return 检测结果（0-不合格，1-合格，2-不存在）
     */
    @PostMapping("/ai-inspect")
    public AjaxResult aiInspect(@RequestBody PartInspectionDTO request) {
        return AjaxResult.success(aiInspectionService.inspect(request));
    }

    /**
     * 获取零件检测结果
     *
     * @param partId 零件ID
     * @return 检测结果详情
     */
    @GetMapping("/result/{partId}")
    public ResponseEntity<Map<String, Object>> getInspectionResult(@PathVariable Long partId) {
        Part part = partInspectionService.getPartById(partId);
        if (part == null) {
            return ResponseEntity.notFound().build();
        }

        // 构建返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("partId", part.getId());
        result.put("partName", part.getPartName());
        result.put("isQualified", part.getIsQualified());
        result.put("inspectionTime", part.getInspectionTime());
        result.put("inspectionDetails", part.getInspectionDetails());
        result.put("inspectionSuggestion", part.getInspectionSuggestion());

        return ResponseEntity.ok(result);
    }
}
