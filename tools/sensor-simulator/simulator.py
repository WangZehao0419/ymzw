#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
传感器数据模拟器(云眸智维联调工具)

向 MQTT Broker 周期发布模拟传感器数据,用于演示/联调设备监控链路:
    sensor/{equipmentCode}/{sensorCode} → MqttMessageHandler → SensorDataReceivedEvent → 落库/推送/告警

双模拟器分工:
    - 本脚本管一号机床 EQ-001(TEMP-001 固定温度曲线轮询: 2 分钟一个加工周期,
      23°C 起步升温 → 100°C 顶峰 → 30 秒快速冷却回 23°C 闭环, 曲线每周期越过 75°C 告警线);
    - 同目录 simulator-predict.py 管二号机床 EQ-002(预测告警 PREDICT 演示:
      TEMP-002 线性漂移 + VIB-002 噪声增大 + --backfill 预热回填 + maintenance 维护复位);
    - 两者按设备隔离(topic 前缀 sensor/EQ-001/# 与 sensor/EQ-002/#),
      可同时运行互不干扰。

使用方法:
    1. 安装依赖: pip install paho-mqtt
    2. 启动本机 MQTT Broker(默认 localhost:1883, 如 EMQX / mosquitto)
    3. 启动模拟器: python simulator.py
    4. Ctrl+C 退出

注意: SENSORS 中的 sensorCode 必须与 MySQL equipment_sensor 表的 sensor_code 一致,
      演示前请先核对数据库实际值并修改下方配置。
"""

import json
import math
import random
import threading
import time
import uuid
from datetime import datetime

import paho.mqtt.client as mqtt

# ============================== 配置区 ==============================

BROKER_HOST = "8.145.53.117"
BROKER_PORT = 1883

# 每轮发布间隔(秒): 一轮内所有传感器各发一个点
PUBLISH_INTERVAL_SECONDS = 1.0

# 正弦波完整周期占用点数: 控制正常期曲线起伏速度
# [联动] 与 simulator-predict.py 及后端 ruoyi-alert 的 predict.sine-period 保持一致
SINE_PERIOD_CYCLES = 60

# 传感器列表
# 字段说明:
#   sensorCode / equipmentCode / equipmentId: 业务编码与设备 ID(sensorCode 必须与 equipment_sensor 表一致)
#   profile: 固定值列表轮询模式(可选), 按周期计数器依次取值, 取完从头循环;
#            配置后优先于正弦波/越界机制, 越界状态由值与阈值比较得出
#   normal_min / normal_max: 正弦波模式正常波动范围(正弦波 + 噪声)
#   alert_upper / alert_lower: 越界告警阈值, None 表示不启用该方向越界
#   breach_cycle: 每多少个周期触发一次越界(0 表示从不越界, 仅正弦波模式生效)
#   breach_duration: 越界持续点数(需 >= 2 才能演示告警防抖/持续点数判定, 且应 < breach_cycle)

# TEMP-001 固定温度曲线: 120 点 = 120 秒 = 每 2 分钟一个完整加工周期
# 形态: 23°C 起步 → 前 90 秒线性升温至 100°C 顶峰 → 后 30 秒快速冷却回 23°C(首尾衔接闭环) → 循环
# 曲线每周期第 62~99 秒超过 alert_upper=75°C, 用于演示周期性越界告警
TEMP_PROFILE = [
    # 上升段 第 1~90 秒: 23 → 100
    23.0, 23.9, 24.7, 25.6, 26.5, 27.3, 28.2, 29.1, 29.9, 30.8,
    31.7, 32.5, 33.4, 34.2, 35.1, 36.0, 36.8, 37.7, 38.6, 39.4,
    40.3, 41.2, 42.0, 42.9, 43.8, 44.6, 45.5, 46.4, 47.2, 48.1,
    49.0, 49.8, 50.7, 51.6, 52.4, 53.3, 54.1, 55.0, 55.9, 56.7,
    57.6, 58.5, 59.3, 60.2, 61.1, 61.9, 62.8, 63.7, 64.5, 65.4,
    66.3, 67.1, 68.0, 68.9, 69.7, 70.6, 71.4, 72.3, 73.2, 74.0,
    74.9, 75.8, 76.6, 77.5, 78.4, 79.2, 80.1, 81.0, 81.8, 82.7,
    83.6, 84.4, 85.3, 86.2, 87.0, 87.9, 88.8, 89.6, 90.5, 91.3,
    92.2, 93.1, 93.9, 94.8, 95.7, 96.5, 97.4, 98.3, 99.1, 100.0,
    # 下降段 第 91~120 秒: 100 → 23(快速冷却, 末值回到起点温度, 轮询时首尾无缝衔接)
    97.4, 94.9, 92.3, 89.7, 87.2, 84.6, 82.0, 79.5, 76.9, 74.3,
    71.8, 69.2, 66.6, 64.1, 61.5, 58.9, 56.4, 53.8, 51.2, 48.7,
    46.1, 43.5, 41.0, 38.4, 35.8, 33.3, 30.7, 28.1, 25.6, 23.0,
]

SENSORS = [
    {
        "sensorCode": "TEMP-001",
        "equipmentCode": "EQ-001",
        "equipmentId": 1,
        "name": "温度传感器",
        "unit": "°C",
        "normal_min": 20.0,
        "normal_max": 60.0,
        "alert_upper": 75.0,
        "alert_lower": None,
        # 固定曲线轮询, 曲线本身周期性越界, 无需再配 breach_cycle/breach_duration
        "profile": TEMP_PROFILE,
    },
    {
        "sensorCode": "HUM-001",
        "equipmentCode": "EQ-001",
        "equipmentId": 1,
        "name": "湿度传感器",
        "unit": "%RH",
        "normal_min": 30.0,
        "normal_max": 70.0,
        "alert_upper": None,
        "alert_lower": None,
        "breach_cycle": 0,
        "breach_duration": 0,
    },
    {
        "sensorCode": "VIB-001",
        "equipmentCode": "EQ-002",
        "equipmentId": 1,
        "name": "振动传感器",
        "unit": "mm/s",
        "normal_min": 0.0,
        "normal_max": 5.0,
        "alert_upper": None,
        "alert_lower": None,
        "breach_cycle": 0,
        "breach_duration": 0,
    },
]

# ============================== 数据生成 ==============================


def in_breach(cycle, cfg):
    """判断当前周期是否处于越界窗口: cycle % breach_cycle == 0 起连续 breach_duration 个点"""
    breach_cycle = cfg["breach_cycle"]
    duration = cfg["breach_duration"]
    if not breach_cycle or not duration:
        return False
    # 两个方向的阈值都没配, 则无从越界
    if cfg["alert_upper"] is None and cfg["alert_lower"] is None:
        return False
    return cycle % breach_cycle < duration


def gen_value(cycle, cfg):
    """生成一个数据点, 返回 (值, 是否越界)

    固定曲线模式(cfg 含 profile): 按周期计数器轮询固定值列表, 越界状态由值与阈值比较得出;
    正弦波模式(其余传感器): 正弦波在 [normal_min, normal_max] 内波动 + 随机噪声(量程 ±5%),
    越界期冲高/下探(超出阈值 10%~20%)。
    """
    profile = cfg.get("profile")
    if profile:
        # cycle 从 1 开始, 取模实现取完列表后从头循环
        value = profile[(cycle - 1) % len(profile)]
        breach = (cfg["alert_upper"] is not None and value > cfg["alert_upper"]) or \
                 (cfg["alert_lower"] is not None and value < cfg["alert_lower"])
        return value, breach

    if in_breach(cycle, cfg):
        if cfg["alert_upper"] is not None:
            # 有上限优先向上冲高
            base = cfg["alert_upper"]
            return base * (1.0 + random.uniform(0.10, 0.20)), True
        # 无上限则向下探低(基于阈值绝对值, 供负/零下限时仍能产生偏移)
        base = abs(cfg["alert_lower"])
        return cfg["alert_lower"] - base * random.uniform(0.10, 0.20), True

    mid = (cfg["normal_min"] + cfg["normal_max"]) / 2.0
    amp = (cfg["normal_max"] - cfg["normal_min"]) / 2.0
    value = mid + amp * math.sin(2.0 * math.pi * cycle / SINE_PERIOD_CYCLES)
    # 随机噪声模拟真实采集抖动, 幅度为量程的 ±5%
    value += random.uniform(-0.05, 0.05) * (cfg["normal_max"] - cfg["normal_min"])
    return value, False


# ============================== MQTT 连接 ==============================

# 首次连接成功事件: 发布线程等它, 避免连接建立前的消息被丢弃
connected = threading.Event()

# 每个传感器独立的周期计数器(sensorCode → cycle), 从 1 开始计数
cycle_counters = {cfg["sensorCode"]: 0 for cfg in SENSORS}


def now():
    return datetime.now().strftime("%H:%M:%S")


def on_connect(client, userdata, flags, rc):
    if rc == 0:
        connected.set()
        print(f"[{now()}] [MQTT] 已连接 {BROKER_HOST}:{BROKER_PORT}")
    else:
        print(f"[{now()}] [MQTT] 连接失败 rc={rc}")


def on_disconnect(client, userdata, rc):
    print(f"[{now()}] [MQTT] 连接断开 rc={rc}, 等待自动重连...")


def build_client():
    """client_id 带 uuid 后缀避免多实例冲突; 兼容 paho-mqtt 1.x / 2.x 两种构造签名"""
    client_id = f"sensor-simulator-{uuid.uuid4().hex[:8]}"
    try:
        # paho-mqtt >= 2.0 必须显式指定回调 API 版本, 用 VERSION1 保持旧签名回调
        return mqtt.Client(mqtt.CallbackAPIVersion.VERSION1, client_id=client_id)
    except AttributeError:
        # paho-mqtt 1.x 没有 CallbackAPIVersion
        return mqtt.Client(client_id=client_id)


# ============================== 发布循环 ==============================


def publish_round(client):
    """发布一轮: 每个传感器各发一个点, 各自维护独立的周期计数器"""
    for cfg in SENSORS:
        cycle_counters[cfg["sensorCode"]] += 1
        cycle = cycle_counters[cfg["sensorCode"]]
        value, breach = gen_value(cycle, cfg)

        # 多级 topic: sensor/{equipmentCode}/{sensorCode}
        topic = f"sensor/{cfg['equipmentCode']}/{cfg['sensorCode']}"
        # ISO 格式不带时区后缀, 与 Java LocalDateTime.parse 默认格式兼容
        payload = json.dumps({
            "sensorCode": cfg["sensorCode"],
            "sensorValue": round(value, 2),
            "equipmentId": cfg["equipmentId"],
            "timestamp": datetime.now().isoformat(),
        }, ensure_ascii=False)

        info = client.publish(topic, payload)
        if info.rc != mqtt.MQTT_ERR_SUCCESS:
            print(f"[{now()}] [PUB] 发布失败 rc={info.rc}: {topic}")
        print(f"[{now()}] [PUB] {topic} {cfg['name']}={round(value, 2)}{cfg['unit']}"
              f"{' [越界]' if breach else ''} (cycle={cycle})")


def publish_loop(client):
    # 等首次连接成功再开始发布, 避免连接前的消息被丢弃
    connected.wait()
    while True:
        publish_round(client)
        time.sleep(PUBLISH_INTERVAL_SECONDS)


def main():
    client = build_client()
    client.on_connect = on_connect
    client.on_disconnect = on_disconnect
    # 断线后 1~60 秒指数退避自动重连
    client.reconnect_delay_set(1, 60)

    # 发布循环放守护线程, 主线程跑阻塞的网络循环
    threading.Thread(target=publish_loop, args=(client,), daemon=True).start()
    try:
        client.connect(BROKER_HOST, BROKER_PORT)
        # 阻塞式网络循环(含断线自动重连), Ctrl+C 退出
        client.loop_forever()
    except KeyboardInterrupt:
        print(f"\n[{now()}] 模拟器已停止")
    except Exception as e:
        print(f"[{now()}] [MQTT] 无法连接 {BROKER_HOST}:{BROKER_PORT}: {e}")
        print(f"[{now()}] 请确认 MQTT Broker 已启动后重试")


if __name__ == "__main__":
    main()
