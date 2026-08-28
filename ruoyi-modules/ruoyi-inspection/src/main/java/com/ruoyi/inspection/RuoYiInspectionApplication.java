package com.ruoyi.inspection;

import com.ruoyi.common.security.annotation.EnableCustomConfig;
import com.ruoyi.common.security.annotation.EnableRyFeignClients;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 巡检服务（云眸智维）
 * <p>
 * 负责零件/巡检标准/巡检记录管理、Chroma 向量库同步（HTTP REST）与检索
 * 通过 OpenFeign 调用 AI 模块完成 AI 检测推理
 * </p>
 *
 * @author ruoyi
 */
@EnableCustomConfig
@EnableRyFeignClients
@SpringBootApplication
public class RuoYiInspectionApplication {

    public static void main(String[] args) {
        SpringApplication.run(RuoYiInspectionApplication.class, args);
        System.out.println("(♥◠‿◠)ﾉﾞ  巡检模块启动成功   ლ(´ڡ`ლ)ﾞ");
    }
}
