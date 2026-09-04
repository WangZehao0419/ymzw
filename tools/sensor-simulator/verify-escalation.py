#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
多级阈值告警升级验证脚本(一次性工具)

按命令行指定的"值x次数"序列向 MQTT 精确推送 TEMP-001 数据点,
用于端到端验证 L1 多规则分级告警与升级链路:
    MQTT sensor/EQ-001/TEMP-001 → equipment 转发 RocketMQ → ruoyi-alert 检测

用法:
    python verify-escalation.py 41x5          # 推 5 个 41 的点
    python verify-escalation.py 41x5 71x2     # 先 41x5 再 71x2, 段间停 2 秒

注意: 运行期间须停掉 simulator.py, 否则随机数据会交错污染防抖计数。
"""

import json
import sys
import time
import uuid
from datetime import datetime

import paho.mqtt.client as mqtt

BROKER_HOST = "localhost"
BROKER_PORT = 1883
# 与 simulator.py 保持一致: TEMP-001 挂在 EQ-001 下
TOPIC = "sensor/EQ-001/TEMP-001"
# 与 simulator.py 的 PUBLISH_INTERVAL_SECONDS 一致, 模拟真实采集节奏
INTERVAL_SECONDS = 0.5
# 两段值之间留检测链路消化时间(防抖计数跨段累计属于预期行为, 停顿只为查询库表方便)
SEGMENT_PAUSE_SECONDS = 2.0


def now():
    return datetime.now().strftime("%H:%M:%S")


def on_connect(client, userdata, flags, rc):
    if rc == 0:
        userdata["connected"].set()
        print(f"[{now()}] [MQTT] 已连接 {BROKER_HOST}:{BROKER_PORT}")
    else:
        print(f"[{now()}] [MQTT] 连接失败 rc={rc}")
        sys.exit(1)


def build_client():
    client_id = f"verify-escalation-{uuid.uuid4().hex[:8]}"
    try:
        return mqtt.Client(mqtt.CallbackAPIVersion.VERSION1, client_id=client_id)
    except AttributeError:
        return mqtt.Client(client_id=client_id)


def publish(client, connected, value):
    connected.wait()
    payload = json.dumps({
        "sensorCode": "TEMP-001",
        "sensorValue": value,
        "equipmentId": 1,
        "timestamp": datetime.now().isoformat(),
    }, ensure_ascii=False)
    info = client.publish(TOPIC, payload)
    if info.rc != mqtt.MQTT_ERR_SUCCESS:
        print(f"[{now()}] [PUB] 发布失败 rc={info.rc}")
        sys.exit(1)
    print(f"[{now()}] [PUB] {TOPIC} TEMP-001={value}")


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)

    # 解析 "41x5" 形式的段: [(41.0, 5), (71.0, 2), ...]
    segments = []
    for arg in sys.argv[1:]:
        try:
            value_str, count_str = arg.lower().split("x")
            segments.append((float(value_str), int(count_str)))
        except ValueError:
            print(f"参数格式错误(应为 值x次数): {arg}")
            sys.exit(1)

    connected = {}
    connected["connected"] = __import__("threading").Event()
    client = build_client()
    client.user_data_set(connected)
    client.on_connect = on_connect
    client.connect(BROKER_HOST, BROKER_PORT)
    client.loop_start()

    try:
        for i, (value, count) in enumerate(segments):
            if i > 0:
                print(f"[{now()}] ---- 段间停 {SEGMENT_PAUSE_SECONDS}s ----")
                time.sleep(SEGMENT_PAUSE_SECONDS)
            for _ in range(count):
                publish(client, connected["connected"], value)
                time.sleep(INTERVAL_SECONDS)
        # 发完停 1 秒让链路收尾(异步落库/推送)
        time.sleep(1.0)
    finally:
        client.loop_stop()
        client.disconnect()
    print(f"[{now()}] 全部推送完成")


if __name__ == "__main__":
    main()
