package com.ruoyi.equipment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.equipment.entity.Workshop;
import org.apache.ibatis.annotations.Mapper;

/**
 * 车间基础信息 Mapper 接口
 * <p>
 * 继承 MyBatis-Plus 的 BaseMapper，自动拥有 CRUD 功能。
 * 车间无复杂关联查询，Wrapper 即可覆盖全部条件，故不需要 XML
 * </p>
 *
 * @author smartartisan
 */
@Mapper
public interface WorkshopMapper extends BaseMapper<Workshop> {
}
