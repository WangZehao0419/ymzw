package com.ruoyi.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.ai.entity.DiagnosisRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI诊断报告Mapper接口
 * <p>
 * 提供诊断报告（diagnosis_record表）的数据库操作方法，
 * CRUD 由 MyBatis-Plus BaseMapper 提供，无需 XML
 * </p>
 *
 * @author smartartisan
 */
@Mapper
public interface DiagnosisRecordMapper extends BaseMapper<DiagnosisRecord> {
}
