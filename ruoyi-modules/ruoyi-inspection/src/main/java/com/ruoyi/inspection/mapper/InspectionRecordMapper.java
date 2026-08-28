package com.ruoyi.inspection.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.inspection.entity.InspectionRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 检测记录 Mapper 接口
 * <p>
 * 继承 MyBatis-Plus 的 BaseMapper，自动拥有 CRUD 功能
 * </p>
 *
 * @author smartartisan
 */
@Mapper
public interface InspectionRecordMapper extends BaseMapper<InspectionRecord> {

}
