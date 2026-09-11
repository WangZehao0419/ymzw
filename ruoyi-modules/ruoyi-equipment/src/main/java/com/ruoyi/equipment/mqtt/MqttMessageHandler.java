package com.ruoyi.equipment.mqtt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.equipment.config.MqttProperties;
import com.ruoyi.equipment.event.SensorDataReceivedEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Iterator;
import java.util.Map;

/**
 * MQTT 消息处理器(接入层)
 * <p>
 * 订阅 sensor/# 主题,解析传感器数据 JSON 后发布 SensorDataReceivedEvent。
 * 另支持数采网关经固定主题 sensor/ 上报的 OPC-UA 批量 JSON 报文(顶层含 values
 * 节点键值对,单条约 40 项,每项作为独立事件发布)。
 * 本模块是传感器数据唯一入口:落库(MySQL/TDengine)、实时推送、AI 预测告警、
 * RocketMQ 转发告警模块,均由独立监听器消费,实现事件驱动解耦。
 * </p>
 *
 * @author smartartisan
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MqttMessageHandler implements MqttCallbackExtended {

    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final MqttProperties mqttProperties;

    @Autowired(required = false)
    private MqttClient mqttClient;

    @PostConstruct
    public void init() {
        if (mqttClient == null) {
            log.warn("[MQTT] 客户端未初始化,消息处理器不启动");
            return;
        }
        mqttClient.setCallback(this);
        log.info("[MQTT] 消息处理器已注册");
    }

    /**
     * 连接建立完成回调(含自动重连场景)
     * <p>
     * paho 的 automaticReconnect 只恢复 TCP/会话连接,不恢复订阅
     * (cleanSession 默认 true,订阅随旧会话丢弃)。若不在重连成功后
     * 重新订阅,Broker 重启等断线场景下服务会"假活"——连接正常但
     * 收不到任何消息。故此处对 reconnect 场景强制重订阅。
     * </p>
     */
    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        if (!reconnect) {
            return; // 首次连接的订阅由 MqttConfig 的 SmartLifecycle 负责,此处不重复
        }
        try {
            mqttClient.subscribe(mqttProperties.getTopic(), mqttProperties.getQos());
            log.info("[MQTT] 自动重连成功,已重新订阅主题: {}", mqttProperties.getTopic());
        } catch (MqttException e) {
            // 重订阅失败只记日志:订阅丢失期间消息丢弃,不影响连接本身
            log.error("[MQTT] 重连后重新订阅失败: topic={}, error={}", mqttProperties.getTopic(), e.getMessage());
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("[MQTT] 连接断开: {}", cause.getMessage());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        String payload = new String(message.getPayload(), StandardCharsets.UTF_8);
        if (!topic.startsWith("sensor/")) {
            log.debug("[MQTT] 忽略非 sensor 主题: {}", topic);
            return;
        }

        try {
            JsonNode node = objectMapper.readTree(payload);

            // OPC-UA 批量报文走固定主题 "sensor/",Java 中 "sensor/".split("/") 仅得 1 段,
            // 基于段数的单传感器解析对它必然失效(会被"无法识别的主题段数"分支丢弃),
            // 故改按 payload 结构识别:顶层含 values 对象即批量报文,交由专用分支处理后直接返回
            if (node.has("values") && node.get("values").isObject()) {
                handleOpcUaBatchMessage(topic, node);
                return;
            }

            // 先解析 topic 再取 payload 字段:多级 topic 中 equipmentCode/sensorCode 以 topic 为权威,
            // payload 里的 sensorCode 仅作交叉校验,避免两个来源不一致时数据归属错乱
            String[] parts = topic.split("/");
            String equipmentCode;
            String sensorCode;

            if (parts.length == 3) {
                // 新格式 sensor/{equipmentCode}/{sensorCode}:设备编码与传感器编码均从 topic 提取
                equipmentCode = parts[1];
                sensorCode = parts[2];
                if (equipmentCode.isEmpty() || sensorCode.isEmpty()) {
                    log.warn("[MQTT] 多级主题存在空段,忽略: {}", topic);
                    return;
                }
                // topic 为权威:payload 中 sensorCode 不一致时仅告警不阻断,以 topic 为准
                String payloadCode = node.path("sensorCode").asText(null);
                if (payloadCode != null && !payloadCode.isEmpty() && !payloadCode.equals(sensorCode)) {
                    log.warn("[MQTT] payload sensorCode({}) 与 topic sensorCode({}) 不一致,以 topic 为准: {}",
                            payloadCode, sensorCode, topic);
                }
            } else if (parts.length == 2) {
                // 存量格式 sensor/{sensorCode}:无设备编码,sensorCode 只能从 payload 取
                equipmentCode = null;
                sensorCode = node.path("sensorCode").asText(null);
                if (sensorCode == null || sensorCode.isEmpty()) {
                    log.warn("[MQTT] 二段主题消息缺少 sensorCode,忽略: topic={}, payload={}", topic, payload);
                    return;
                }
            } else {
                log.warn("[MQTT] 无法识别的主题段数,忽略: {}", topic);
                return;
            }

            // sensorValue 只能来自 payload(缺失则无法判定数值,保持原有忽略行为)
            double sensorValue = node.path("sensorValue").asDouble(Double.NaN);
            if (Double.isNaN(sensorValue)) {
                log.warn("[MQTT] 消息缺少必填字段 sensorValue: {}", payload);
                return;
            }

            int equipmentId = node.path("equipmentId").asInt(0);
            LocalDateTime ts = parseTimestamp(node.path("timestamp").asText(null));

            // 发布领域事件,由各监听器独立消费(持久化/推送/AI告警/MQ转发)
            eventPublisher.publishEvent(new SensorDataReceivedEvent(
                    this, sensorCode, sensorValue, equipmentId, ts, topic, equipmentCode));
            log.info("[MQTT] 事件已发布: code={}, equipmentCode={}, equipmentId={}, value={}, ts={}",
                    sensorCode, equipmentCode, equipmentId, sensorValue, ts);
        } catch (Exception e) {
            log.error("[MQTT] 消息处理异常: topic={}, payload={}, error={}", topic, payload, e.getMessage());
        }
    }

    /**
     * 处理 OPC-UA 批量采集报文(数采网关经固定主题 sensor/ 上报)
     * <p>
     * 单条报文携带一组 OPC-UA 节点键值对(约 40 项),key 为完整节点 ID
     * (如 ns=2;s=-Channel-Spindle-actSpeed)。固定主题不携带编码信息,
     * 设备归属只能取自报文内的 node 字段。
     * </p>
     */
    private void handleOpcUaBatchMessage(String topic, JsonNode root) {
        JsonNode values = root.path("values");
        // 空批次说明本轮采集无任何数据,发布无意义事件只会污染下游,告警后直接返回
        if (values.isEmpty()) {
            log.warn("[MQTT] OPC-UA 批量报文 values 为空对象,忽略: topic={}", topic);
            return;
        }

        // 部分节点采集出错时,values 中其余节点数值通常仍有效,故仅告警不中断
        JsonNode errors = root.path("errors");
        if (errors.isObject() && !errors.isEmpty()) {
            log.warn("[MQTT] OPC-UA 批量报文存在采集错误,继续处理有效值: topic={}, errors={}", topic, errors);
        }

        // node 为网关侧采集组配置的设备标识,作为 equipmentCode 透传给下游
        String equipmentCode = root.path("node").asText(null);

        // 批量报文时间戳为 epoch 毫秒数值,与存量报文的 ISO 文本格式不同,
        // 故在本分支内联解析,不复用按文本解析的 parseTimestamp
        long ts = root.path("timestamp").asLong(0);
        LocalDateTime dataTs = ts > 0
                ? LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.systemDefault())
                : LocalDateTime.now();

        int published = 0;
        int total = 0;
        Iterator<Map.Entry<String, JsonNode>> fields = values.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            total++;
            JsonNode valueNode = entry.getValue();
            // 下游事件链路按 Double 数值消费:数值直接取值,布尔状态量归一化为
            // 1.0/0.0 以便统一存储与阈值判断;字符串(如主轴名)、null 等无法量化,
            // 跳过避免产生脏数据
            Double sensorValue;
            if (valueNode.isNumber()) {
                sensorValue = valueNode.asDouble();
            } else if (valueNode.isBoolean()) {
                sensorValue = valueNode.asBoolean() ? 1.0 : 0.0;
            } else {
                log.debug("[MQTT] OPC-UA 节点值类型不可量化,跳过: topic={}, key={}, type={}",
                        topic, entry.getKey(), valueNode.getNodeType());
                continue;
            }

            // sensorCode 原样保留完整节点 ID:节点 ID 本身即唯一标识,
            // 任何截取都会丢失命名空间等归属信息,导致下游无法回查
            eventPublisher.publishEvent(new SensorDataReceivedEvent(
                    this, entry.getKey(), sensorValue, 0, dataTs, topic, equipmentCode));
            published++;
        }

        // 单条约 40 项,逐条 info 会刷屏,仅打一条汇总日志
        log.info("[MQTT] OPC-UA 批量事件发布完成: topic={}, node={}, timestamp={}, 发布 {}/{} 项",
                topic, equipmentCode, dataTs, published, total);
    }

    /**
     * 解析时间戳,失败或缺失时使用当前时间
     */
    private LocalDateTime parseTimestamp(String tsStr) {
        if (tsStr == null || tsStr.isEmpty()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(tsStr);
        } catch (Exception e) {
            log.warn("[MQTT] 时间戳解析失败,使用当前时间: {}", tsStr);
            return LocalDateTime.now();
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // 只订阅不发布,空实现
    }
}
