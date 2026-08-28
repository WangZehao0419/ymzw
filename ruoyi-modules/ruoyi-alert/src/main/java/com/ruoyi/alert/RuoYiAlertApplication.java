package com.ruoyi.alert;

import com.ruoyi.common.security.annotation.EnableCustomConfig;
import com.ruoyi.common.security.annotation.EnableRyFeignClients;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 告警服务（云眸智维）
 * <p>
 * 负责告警规则管理、MQTT 告警消息接入、TDengine 时序数据持久化、规则检测事件处理
 * 双数据源：MySQL（主源，业务表）+ TDengine（时序库，传感器/告警明细）
 * </p>
 *
 * @author ruoyi
 */
@EnableCustomConfig
@EnableRyFeignClients
@SpringBootApplication
public class RuoYiAlertApplication {

    public static void main(String[] args) {
        SpringApplication.run(RuoYiAlertApplication.class, args);
        System.out.println("(♥◠‿◠)ﾉﾞ  告警模块启动成功   ლ(´ڡ`ლ)ﾞ");
    }
}
