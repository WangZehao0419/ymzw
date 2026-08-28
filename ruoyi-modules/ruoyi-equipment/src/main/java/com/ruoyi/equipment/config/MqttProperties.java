package com.ruoyi.equipment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MQTT 配置属性类
 * <p>
 * 绑定 application.yml 中 mqtt 前缀的配置项，支持通过环境变量覆盖默认值。
 * </p>
 *
 * @author smartartisan
 */
@Data
@ConfigurationProperties(prefix = "mqtt")
public class MqttProperties {

    /**
     * MQTT Broker 连接地址，tcp:// 协议
     */
    private String brokerUrl = "tcp://localhost:1883";

    /**
     * MQTT 客户端 ID
     */
    private String clientId = "cloud-equipment";

    /**
     * MQTT 用户名
     */
    private String username = "";

    /**
     * MQTT 密码
     */
    private String password = "";

    /**
     * 订阅主题，支持通配符
     */
    private String topic = "sensor/#";

    /**
     * QoS 服务质量等级：0-最多一次, 1-至少一次, 2-仅一次
     */
    private int qos = 1;

    /**
     * 心跳间隔（秒）
     */
    private int keepAlive = 60;

    /**
     * 连接超时（秒）
     */
    private int connectionTimeout = 30;

    /**
     * 是否自动重连
     */
    private boolean automaticReconnect = true;
}