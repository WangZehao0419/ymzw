package com.ruoyi.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.ai.entity.DiagnosisRecord;
import com.ruoyi.ai.entity.query.DiagnosisRecordQuery;
import com.ruoyi.ai.mapper.DiagnosisRecordMapper;
import com.ruoyi.ai.repository.BaseRepository;
import com.ruoyi.ai.service.DiagnosisRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * AI诊断报告服务实现类
 * <p>
 * 诊断报告的落库与按设备/传感器的分页历史查询
 * </p>
 *
 * @author smartartisan
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiagnosisRecordServiceImpl extends BaseRepository<DiagnosisRecordMapper, DiagnosisRecord> implements DiagnosisRecordService {

    private final DiagnosisRecordMapper diagnosisRecordMapper;

    @Override
    public DiagnosisRecordMapper getBaseMapper() {
        return diagnosisRecordMapper;
    }

    /**
     * 保存诊断报告
     * <p>
     * created_time 由应用侧显式赋值而非依赖数据库默认值：
     * 落库即回填实体时间戳，调用方无需回查即可拿到完整记录
     * </p>
     */
    @Override
    public DiagnosisRecord saveReport(Integer equipmentId, String sensorCode, String report) {
        DiagnosisRecord record = new DiagnosisRecord();
        record.setEquipmentId(equipmentId);
        record.setSensorCode(sensorCode);
        record.setReport(report);
        record.setCreatedTime(LocalDateTime.now());
        this.save(record);
        log.info("诊断报告已落库: recordId={}, equipmentId={}, sensorCode={}",
                record.getId(), equipmentId, sensorCode);
        return record;
    }

    @Override
    public IPage<DiagnosisRecord> page(DiagnosisRecordQuery query) {
        LambdaQueryWrapper<DiagnosisRecord> wrapper = new LambdaQueryWrapper<>();
        if (query.getEquipmentId() != null) {
            wrapper.eq(DiagnosisRecord::getEquipmentId, query.getEquipmentId());
        }
        if (StringUtils.hasText(query.getSensorCode())) {
            wrapper.eq(DiagnosisRecord::getSensorCode, query.getSensorCode());
        }
        // 最新诊断在前，符合"先看最近一次报告"的使用习惯
        wrapper.orderByDesc(DiagnosisRecord::getId);

        Page<DiagnosisRecord> page = new Page<>(query.getPage(), query.getPageSize());
        return this.page(page, wrapper);
    }
}
