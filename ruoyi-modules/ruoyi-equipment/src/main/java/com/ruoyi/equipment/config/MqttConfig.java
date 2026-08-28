package com.ruoyi.equipment.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MQTT 配置类
 * <p>
 * 负责创建 MQTT 客户端、连接选项，并通过 SmartLifecycle 在 Spring 容器就绪后自动连接 Broker 并订阅主题。
 * Broker 不可连时不影响服务启动，仅打印警告日志。
 * </p>
 *
 * @author smartartisan
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(MqttProperties.class)
public class MqttConfig {

    private final MqttProperties mqttProperties;

    /**
     * 创建 MQTT 连接选项 Bean
     * <p>
     * 从配置中读取 broker-url、认证信息、心跳间隔、连接超时、自动重连等参数。
     * </p>
     */
    @Bean
    public MqttConnectOptions mqttConnectOptions() {
        MqttConnectOptions options = new MqttConnectOptions();
        // 设置用户名密码，仅在非空时设置以免空值覆盖
        if (!mqttProperties.getUsername().isEmpty()) {
            options.setUserName(mqttProperties.getUsername());
        }
        if (!mqttProperties.getPassword().isEmpty()) {
            options.setPassword(mqttProperties.getPassword().toCharArray());
        }
        options.setKeepAliveInterval(mqttProperties.getKeepAlive());
        options.setConnectionTimeout(mqttProperties.getConnectionTimeout());
        options.setAutomaticReconnect(mqttProperties.isAutomaticReconnect());
        return options;
    }

    /**
     * 创建 MQTT 客户端 Bean
     * <p>
     * 容器销毁时自动调用 disconnect() 断开连接。
     * </p>
     */
    @Bean(destroyMethod = "disconnect")
    public MqttClient mqttClient() throws MqttException {
        return new MqttClient(mqttProperties.getBrokerUrl(), mqttProperties.getClientId());
    }

    /**
     * MQTT 连接生命周期 Bean
     * <p>
     * 使用 SmartLifecycle 而非 @PostConstruct，确保所有 Bean 依赖就绪后启动。
     * getPhase() 返回 Integer.MAX_VALUE，保证最后启动。
     * Broker 不可连时仅打印 WARN 日志，不抛异常，不影响服务启动。
     * </p>
     */
    @Bean
    public SmartLifecycle mqttSmartLifecycle(MqttClient mqttClient, MqttConnectOptions mqttConnectOptions) {
        return new SmartLifecycle() {

            private volatile boolean running = false;

            @Override
            public void start() {
                String brokerUrl = mqttProperties.getBrokerUrl();
                if (brokerUrl == null || brokerUrl.isEmpty()) {
                    log.warn("MQTT 未配置，跳过连接");
                    return;
                }

                try {
                    mqttClient.connect(mqttConnectOptions);
                    mqttClient.subscribe(mqttProperties.getTopic(), mqttProperties.getQos());
                    log.info("MQTT 客户端已连接到 {}，订阅主题 {}", brokerUrl, mqttProperties.getTopic());
                    running = true;
                } catch (MqttException e) {
                    log.warn("MQTT 连接失败: {}，服务继续启动", e.getMessage());
                }
            }

            @Override
            public void stop() {
                if (mqttClient.isConnected()) {
                    try {
                        mqttClient.disconnect();
                        log.info("MQTT 客户端已断开连接");
                    } catch (MqttException e) {
                        log.warn("MQTT 断开连接异常: {}", e.getMessage());
                    }
                }
                running = false;
            }

            @Override
            public boolean isRunning() {
                return running;
            }

            @Override
            public int getPhase() {
                // 返回最大值，确保在所有其他 Bean 启动后再启动
                return Integer.MAX_VALUE;
            }
        };
    }
}