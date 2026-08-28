package com.ruoyi.ai.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum AgentTypeEnum {

    CHAT("CHAT", "通用对话"),
    PREDICTIVE_ALARM("PREDICTIVE_ALARM", "预测性告警"),
    PART_INSPECTION("PART_INSPECTION", "零件检测"),
    REPORT("REPORT", "报告生成"),
    KNOWLEDGE_QA("KNOWLEDGE_QA", "知识问答"),
    EMBEDDING("EMBEDDING", "向量嵌入");

    private final String code;
    private final String description;

    public static AgentTypeEnum getByCode(String code) {
        if (code == null) return null;
        for (AgentTypeEnum type : values()) {
            if (type.getCode().equals(code)) return type;
        }
        return null;
    }

    public static boolean isValidCode(String code) {
        return getByCode(code) != null;
    }
}