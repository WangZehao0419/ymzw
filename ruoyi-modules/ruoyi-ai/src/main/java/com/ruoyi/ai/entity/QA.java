package com.ruoyi.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 问答记录实体类
 * <p>
 * 用于存储AI对话的问答记录，对应 qna_management 表
 * </p>
 *
 * @author smartartisan
 */
@Data
@TableName("qna_management")
public class QA {

    /**
     * 问答记录ID
     */
    @TableId(value = "qna_id", type = IdType.ASSIGN_UUID)
    private String qnaId;

    /**
     * 用户ID
     */
    @TableField("user_id")
    private String userId;

    /**
     * 问题内容
     */
    @TableField("question")
    private String question;

    /**
     * 回答内容
     */
    @TableField("answer")
    private String answer;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 用户名
     */
    @TableField("username")
    private String username;
}
