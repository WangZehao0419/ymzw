#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
传感器数据模拟器——平稳运行版(一号机床 EQ-001 专用, 云眸智维联调工具)

向 MQTT Broker 周期发布平缓的模拟传感器数据: 数值围绕正常区间小范围波动,
不越界、不漂移, 用于演示"设备健康平稳运行"场景(正常基线/前端大屏/预测对照):
    sensor/{equipmentCode}/{sensorCode} → MqttMessageHandler → 落库/推送/告警

数据形态(闭环轮询):
    - 三条曲线均由双频正弦生成: 基波平缓起伏 + 1/4 幅度的 7 次谐波模拟采集细节;
    - 谐波频率取基波整数倍, 相位走满整数周期, 首尾值严格相等 → 轮询时无缝闭环;
    - 120 点 = 120 秒 = 每 2 分钟一个完整波动周期, 循环往复;
    - 三个传感器相位彼此错开, 避免三条曲线同步起落显得失真。

双模拟器分工:
    - 本脚本管一号机床 EQ-001 的平稳数据(TEMP-001/HUM-001/VIB-001,
      VIB-001 按数据库归属发布到 sensor/EQ-001/VIB-001);
    - 同目录 simulator.py 管一号机床的大幅加工曲线(阈值告警演示),
      simulator-predict.py 管二号机床 EQ-002(预测告警演示);
    - 注意: 本脚本与 simulator.py 使用相同 sensorCode, 两者不可同时运行
      (同一传感器双数据源会导致曲线交错), 演示正常场景时请先停掉 simulator.py。

使用方法:
    1. 安装依赖: pip install paho-mqtt(或依赖同目录 .deps-paho 离线包自动加载)
    2. 启动 MQTT Broker(默认 8.145.53.117:1883)
    3. 启动模拟器: python simulator-stable.py
    4. Ctrl+C 退出

注意: SENSORS 中的 sensorCode 必须与 MySQL equipment_sensor 表的 sensor_code 一致,
      演示前请先核对数据库实际值并修改下方配置。
"""

import json
import math
import os
import sys
import threading
import time
import uuid
from datetime import datetime

# 依赖自愈: paho-mqtt 可能没装进当前解释器(例如 IDE 用的是独立 SDK 或虚拟环境),
# 这里把脚本同级的离线依赖目录 .deps-paho 加入模块搜索路径,
# 使脚本不依赖 pip 安装即可运行; 已装过 paho 的环境不受影响(优先用 site-packages 版本)
_DEPS_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".deps-paho")
if os.path.isdir(_DEPS_DIR) and _DEPS_DIR not in sys.path:
    sys.path.insert(0, _DEPS_DIR)

import paho.mqtt.client as mqtt

# ============================== 配置区 ==============================

BROKER_HOST = "8.145.53.117"
BROKER_PORT = 1883

# 每轮发布间隔(秒): 一轮内所有传感器各发一个点
PUBLISH_INTERVAL_SECONDS = 1.0

# 一个完整波动周期的点数: 120 点 × 1 秒 = 每 2 分钟闭环一次
PROFILE_POINTS = 120

# 传感器列表(全部挂一号机床 EQ-001, 设备主键 1, 与 equipment_sensor 表归属一致)
# 字段说明:
#   sensorCode / equipmentCode / equipmentId: 业务编码与设备 ID(sensorCode 必须与 equipment_sensor 表一致)
#   center / amplitude: 正常波动中心值与基波幅度(实际范围约为 center ± 1.25×amplitude, 含谐波)
#   phase: 基波初相位(弧度), 三个传感器错开避免曲线同步起落
#   alert_upper / alert_lower: 越界告警阈值(仅用于发布日志展示, 平稳数据不会触发)
SENSORS = [
    {   # 机床舱温 25°C ± 1.5: 恒温车间正常运行区间(告警线 75°C, 远未触及)
        "sensorCode": "TEMP-001",
        "equipmentCode": "EQ-001",
        "equipmentId": 1,
        "name": "温度传感器",
        "unit": "°C",
        "center": 25.0,
        "amplitude": 1.5,
        "phase": 0.0,
        "alert_upper": 75.0,
        "alert_lower": None,
    },
    {   # 舱内湿度 50%RH ± 5: 舒适区间 40~60 的中段小幅波动
        "sensorCode": "HUM-001",
        "equipmentCode": "EQ-001",
        "equipmentId": 1,
        "name": "湿度传感器",
        "unit": "%RH",
        "center": 50.0,
        "amplitude": 5.0,
        "phase": 2.1,
        "alert_upper": None,
        "alert_lower": None,
    },
    {   # 振动速度 1.0 mm/s ± 0.2: ISO 20816-3 Group 2 A 区(新设备 ≤1.4), 运行状态良好
        "sensorCode": "VIB-001",
        "equipmentCode": "EQ-001",
        "equipmentId": 1,
        "name": "振动传感器",
        "unit": "mm/s",
        "center": 1.0,
        "amplitude": 0.2,
        "phase": 4.2,
        "alert_upper": None,
        "alert_lower": None,
    },
]

# ============================== 数据生成 ==============================


def build_profile(center, amplitude, phase):
    """生成一条闭环平稳曲线: 基波正弦 + 1/6 幅度 7 次谐波

    两个频率均为基波(120 点/周期)的整数倍, 相位走满整数周期,
    因此 profile[0] 与"下一个周期的 profile[0]"相位连续, 首尾严格闭环;
    7 次谐波让曲线带上细小起伏, 比纯正弦更接近真实采集数据
    (幅度压到基波 1/6, 保证相邻点跳变平缓)。
    """
    profile = []
    for i in range(PROFILE_POINTS):
        base = center + amplitude * math.sin(2.0 * math.pi * i / PROFILE_POINTS + phase)
        detail = (amplitude / 6.0) * math.sin(2.0 * math.pi * 7 * i / PROFILE_POINTS + phase)
        profile.append(round(base + detail, 2))
    return profile


# 启动时一次性生成三条固定曲线(与 simulator.py 的固定 profile 语义一致: 数值确定, 轮询复现)
for _cfg in SENSORS:
    _cfg["profile"] = build_profile(_cfg["center"], _cfg["amplitude"], _cfg["phase"])


def gen_value(cycle, cfg):
    """生成一个数据点, 返回 (值, 是否越界)

    按周期计数器轮询 profile 固定值列表, 取完从头循环(首尾衔接闭环);
    越界状态由值与阈值比较得出(严格大于/小于), 平稳数据正常情况下恒为 False。
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
    client_id = f"sensor-simulator-stable-{uuid.uuid4().hex[:8]}"
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

        # 多级 topic: sensor/{equipmentCode}/{sensorCode}(与数据库归属一致)
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
    print(f"[{now()}] [提示] 平稳运行模拟器: EQ-001 三传感器围绕正常区间小范围波动, "
          f"每 {PROFILE_POINTS} 点({int(PROFILE_POINTS * PUBLISH_INTERVAL_SECONDS)} 秒)闭环轮询")
    print(f"[{now()}] [提示] 与 simulator.py 共用 sensorCode, 不可同时运行")

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
