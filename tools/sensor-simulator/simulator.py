#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
传感器数据模拟器(云眸智维联调工具)

向 MQTT Broker 周期发布模拟传感器数据,用于演示/联调设备监控链路:
    sensor/{equipmentCode}/{sensorCode} → MqttMessageHandler → SensorDataReceivedEvent → 落库/推送/告警

双模拟器分工:
    - 本脚本管一号机床 EQ-001 的固定曲线轮询(每 2 分钟一个加工周期, 首尾闭环):
      TEMP-001 温度 23°C → 100°C → 23°C(每周期第 62~99 秒越过 75°C 告警线);
      HUM-001 湿度 40%RH → 90%RH → 40%RH(未配告警阈值);
      另含 EQ-002 的 VIB-001 振动 0.5 → 4.5 → 0.5 mm/s(顶峰触及 ISO 20816 C/D 分界, 未配告警阈值);
    - 同目录 simulator-predict.py 管二号机床 EQ-002(预测告警 PREDICT 演示:
      TEMP-002 线性漂移 + VIB-002 噪声增大 + --backfill 预热回填 + maintenance 维护复位);
    - 两者按设备隔离(topic 前缀 sensor/EQ-001/# 与 sensor/EQ-002/#),
      可同时运行互不干扰(本脚本的 VIB-001 例外, 发布到 sensor/EQ-002/VIB-001)。

使用方法:
    1. 安装依赖: pip install paho-mqtt
    2. 启动本机 MQTT Broker(默认 localhost:1883, 如 EMQX / mosquitto)
    3. 启动模拟器: python simulator.py
    4. Ctrl+C 退出

注意: SENSORS 中的 sensorCode 必须与 MySQL equipment_sensor 表的 sensor_code 一致,
      演示前请先核对数据库实际值并修改下方配置。
"""

import json
import threading
import time
import uuid
from datetime import datetime

import paho.mqtt.client as mqtt

# ============================== 配置区 ==============================

BROKER_HOST = "47.114.55.60"
BROKER_PORT = 1883

# 每轮发布间隔(秒): 一轮内所有传感器各发一个点
PUBLISH_INTERVAL_SECONDS = 1.0

# 传感器列表
# 字段说明:
#   sensorCode / equipmentCode / equipmentId: 业务编码与设备 ID(sensorCode 必须与 equipment_sensor 表一致)
#   profile: 固定值列表, 按周期计数器依次取值, 取完从头循环(首尾衔接闭环)
#   alert_upper / alert_lower: 越界告警阈值(严格大于/小于判定), None 表示不启用该方向越界

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

# HUM-001 固定湿度曲线: 120 点 = 120 秒 = 每 2 分钟一个完整周期(与温度曲线同步)
# 形态: 40%RH 起步 → 前 90 秒线性升至 90%RH 顶峰(模拟加工中油雾/切削液蒸发增湿)
#       → 后 30 秒快速回落回 40%RH(首尾衔接闭环) → 循环
HUM_PROFILE = [
    # 上升段 第 1~90 秒: 40 → 90
    40.0, 40.6, 41.1, 41.7, 42.2, 42.8, 43.4, 43.9, 44.5, 45.1,
    45.6, 46.2, 46.7, 47.3, 47.9, 48.4, 49.0, 49.6, 50.1, 50.7,
    51.2, 51.8, 52.4, 52.9, 53.5, 54.0, 54.6, 55.2, 55.7, 56.3,
    56.9, 57.4, 58.0, 58.5, 59.1, 59.7, 60.2, 60.8, 61.3, 61.9,
    62.5, 63.0, 63.6, 64.2, 64.7, 65.3, 65.8, 66.4, 67.0, 67.5,
    68.1, 68.7, 69.2, 69.8, 70.3, 70.9, 71.5, 72.0, 72.6, 73.1,
    73.7, 74.3, 74.8, 75.4, 76.0, 76.5, 77.1, 77.6, 78.2, 78.8,
    79.3, 79.9, 80.4, 81.0, 81.6, 82.1, 82.7, 83.3, 83.8, 84.4,
    84.9, 85.5, 86.1, 86.6, 87.2, 87.8, 88.3, 88.9, 89.4, 90.0,
    # 下降段 第 91~120 秒: 90 → 40(快速回落, 末值回到起点湿度, 轮询时首尾无缝衔接)
    88.3, 86.7, 85.0, 83.3, 81.7, 80.0, 78.3, 76.7, 75.0, 73.3,
    71.7, 70.0, 68.3, 66.7, 65.0, 63.3, 61.7, 60.0, 58.3, 56.7,
    55.0, 53.3, 51.7, 50.0, 48.3, 46.7, 45.0, 43.3, 41.7, 40.0,
]

# VIB-001 固定振动曲线: 120 点 = 120 秒 = 每 2 分钟一个完整周期(与温度/湿度曲线同步)
# 形态: 0.5 mm/s 起步 → 前 90 秒线性升至 4.5 mm/s 顶峰 → 后 30 秒快速回落回 0.5(首尾衔接闭环) → 循环
# ISO 20816-3 Group 2 分区参考: A ≤1.4 / B 1.4~2.8 / C 2.8~4.5 / D >4.5 (mm/s RMS)
# 曲线第 53~102 秒处于 C 区(不满意, 需计划检修), 顶峰 4.5 恰好触及 C/D 分界但不越过
VIB_PROFILE = [
    # 上升段 第 1~90 秒: 0.5 → 4.5
    0.5, 0.54, 0.59, 0.63, 0.68, 0.72, 0.77, 0.81, 0.86, 0.9,
    0.95, 0.99, 1.04, 1.08, 1.13, 1.17, 1.22, 1.26, 1.31, 1.35,
    1.4, 1.44, 1.49, 1.53, 1.58, 1.62, 1.67, 1.71, 1.76, 1.8,
    1.85, 1.89, 1.94, 1.98, 2.03, 2.07, 2.12, 2.16, 2.21, 2.25,
    2.3, 2.34, 2.39, 2.43, 2.48, 2.52, 2.57, 2.61, 2.66, 2.7,
    2.75, 2.79, 2.84, 2.88, 2.93, 2.97, 3.02, 3.06, 3.11, 3.15,
    3.2, 3.24, 3.29, 3.33, 3.38, 3.42, 3.47, 3.51, 3.56, 3.6,
    3.65, 3.69, 3.74, 3.78, 3.83, 3.87, 3.92, 3.96, 4.01, 4.05,
    4.1, 4.14, 4.19, 4.23, 4.28, 4.32, 4.37, 4.41, 4.46, 4.5,
    # 下降段 第 91~120 秒: 4.5 → 0.5(快速回落, 末值回到起点, 轮询时首尾无缝衔接)
    4.37, 4.23, 4.1, 3.97, 3.83, 3.7, 3.57, 3.43, 3.3, 3.17,
    3.03, 2.9, 2.77, 2.63, 2.5, 2.37, 2.23, 2.1, 1.97, 1.83,
    1.7, 1.57, 1.43, 1.3, 1.17, 1.03, 0.9, 0.77, 0.63, 0.5,
]

SENSORS = [
    {
        "sensorCode": "TEMP-001",
        "equipmentCode": "EQ-001",
        "equipmentId": 1,
        "name": "温度传感器",
        "unit": "°C",
        "alert_upper": 75.0,
        "alert_lower": None,
        "profile": TEMP_PROFILE,
    },
    {
        "sensorCode": "HUM-001",
        "equipmentCode": "EQ-001",
        "equipmentId": 1,
        "name": "湿度传感器",
        "unit": "%RH",
        # 曲线峰值 90%RH 超规范上限(≤80%RH), 未配 alert_upper 不会告警;
        # 需演示湿度告警时给 alert_upper 设 80 即可
        "alert_upper": None,
        "alert_lower": None,
        "profile": HUM_PROFILE,
    },
    {
        "sensorCode": "VIB-001",
        "equipmentCode": "EQ-002",
        "equipmentId": 1,
        "name": "振动传感器",
        "unit": "mm/s",
        # 顶峰 4.5 恰触及 ISO 20816 C/D 分界但不越过, 未配 alert_upper 不会告警;
        # 需演示振动告警时给 alert_upper 设 4.5 以下的值(设 4.5 时因严格大于判定不会告警)
        "alert_upper": None,
        "alert_lower": None,
        "profile": VIB_PROFILE,
    },
]

# ============================== 数据生成 ==============================


def gen_value(cycle, cfg):
    """生成一个数据点, 返回 (值, 是否越界)

    按周期计数器轮询 profile 固定值列表, 取完从头循环;
    越界状态由值与阈值比较得出(严格大于/小于)。
    """
    profile = cfg["profile"]
    # cycle 从 1 开始, 取模实现取完列表后从头循环
    value = profile[(cycle - 1) % len(profile)]
    breach = (cfg["alert_upper"] is not None and value > cfg["alert_upper"]) or \
             (cfg["alert_lower"] is not None and value < cfg["alert_lower"])
    return value, breach


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
