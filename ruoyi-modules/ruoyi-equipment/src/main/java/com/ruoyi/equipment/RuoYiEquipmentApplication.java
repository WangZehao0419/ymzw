package com.ruoyi.equipment;

import com.ruoyi.common.security.annotation.EnableCustomConfig;
import com.ruoyi.common.security.annotation.EnableRyFeignClients;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 设备管理服务（云眸智维）
 * <p>
 * 负责设备/传感器/传感器监测数据管理、MQTT 设备消息接入、NDJSON 流式实时推送
 * </p>
 *
 * @author ruoyi
 */
@EnableCustomConfig
@EnableRyFeignClients
@SpringBootApplication
public class RuoYiEquipmentApplication {

    public static void main(String[] args) {
        SpringApplication.run(RuoYiEquipmentApplication.class, args);
        System.out.println("(♥◠‿◠)ﾉﾞ  设备模块启动成功   ლ(´ڡ`ლ)ﾞ");
    }
}
