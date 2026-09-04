#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
传感器数据模拟器——预测告警演示版(二号机床 EQ-002 专用, 云眸智维联调工具)

向 MQTT Broker 周期发布模拟传感器数据,用于演示/联调预测性维护(PREDICT 告警)链路:
    sensor/{equipmentCode}/{sensorCode} → MqttMessageHandler → 落库/RocketMQ →
    ruoyi-alert PredictTask(基线学习 → MAD/CUSUM 检测 → 趋势外推 → 劣化状态机)

使用方法:
    1. 安装依赖: pip install paho-mqtt
    2. 启动本机 MQTT Broker(默认 localhost:1883, 如 EMQX / mosquitto)
    3. 启动模拟器: python simulator-predict.py --backfill 30
       (--backfill 30 强烈建议: TEMP-002 为新传感器无历史, 预测任务需窗口攒满
        约 480 点(4 分钟)才开检, 回填平稳期数据可跳过等待)
    4. Ctrl+C 退出

使用前提(缺一无 PREDICT 告警):
    - Nacos 中 ruoyi-alert 的 predict.enabled 置 true 并重启模块,
      否则 PredictTask 空转, 预测链路整体关闭;
    - alert_rule 表已有 TEMP-002 规则(上限 75, 持续点数 2, 启用):
      趋势外推与"预测兑现"(RULE 告警联动)均依赖该规则。

预测性维护演示节奏(参考):
    - TEMP-002 线性漂移 +0.08/点(0.16°C/秒): 约 1 分钟 CUSUM 检出漂移并发
      PREDICT 告警, 趋势显著后升级 SEVERE(携带预测越界时刻), 约 4 分钟漂过
      75°C 触发 RULE 告警, PREDICT 告警自动置 RESOLVED(预测兑现);
    - VIB-002 噪声幅度 ×3: 演示 MAD 突变检测(体制变化), 无规则仅触发 L2;
    - HUM-002 为正常对照组;
    - 维护指令: 向 maintenance/EQ-002 发布 payload {"action":"reset"},
      清零该设备全部传感器的退化效果(告警置 RESOLVED, 基线重学;
      再次开启退化需重启模拟器)。

双模拟器说明:
    - 本脚本管二号机床 EQ-002(预测告警演示); 同目录 simulator.py 管一号机床
      EQ-001(阈值告警演示);
    - 两者按设备隔离(topic 前缀 sensor/EQ-002/# 与 sensor/EQ-001/#),
      可同时运行互不干扰。

注意: SENSORS 中的 sensorCode 必须与 MySQL equipment_sensor 表的 sensor_code 一致,
      演示前请先核对数据库实际值并修改下方配置。
"""

import argparse
import json
import math
import os
import random
import sys
import threading
import time
import uuid
from datetime import datetime, timedelta

# 依赖自愈: paho-mqtt 可能没装进当前解释器(例如 IDE 用的是独立 SDK 或虚拟环境),
# 这里把脚本同级的离线依赖目录 .deps-paho 加入模块搜索路径,
# 使脚本不依赖 pip 安装即可运行; 已装过 paho 的环境不受影响(优先用 site-packages 版本)
_DEPS_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".deps-paho")
if os.path.isdir(_DEPS_DIR) and _DEPS_DIR not in sys.path:
    sys.path.insert(0, _DEPS_DIR)

import paho.mqtt.client as mqtt

# ============================== 配置区 ==============================

BROKER_HOST = "localhost"
BROKER_PORT = 1883

# 每轮发布间隔(秒): 一轮内所有传感器各发一个点
PUBLISH_INTERVAL_SECONDS = 0.5

# 回填模式(--backfill N)每轮 sleep(秒): 量级控制, 让海量回填轮次快速灌完
# (0.005 秒/轮时, 14400 轮约 72 秒, 即 1~2 分钟内灌完)
BACKFILL_ROUND_SLEEP = 0.005

# 正弦波完整周期占用点数: 控制正常期曲线起伏速度
# 联动说明: 此值与后端 ruoyi-alert 的 predict.sine-period 配置配套使用
# (预测模型按同一正弦周期做基线拟合), 改此处必须同步改后端配置, 反之亦然
SINE_PERIOD_CYCLES = 60

# 传感器列表(全部挂二号机床 EQ-002, 设备主键 2)
# 字段说明:
#   sensorCode / equipmentCode / equipmentId: 业务编码与设备 ID(sensorCode 必须与 equipment_sensor 表一致)
#   normal_min / normal_max: 正常波动范围(正弦波 + 噪声)
#   alert_upper / alert_lower: 越界告警阈值, None 表示不启用该方向越界
#   breach_cycle: 每多少个周期触发一次越界(0 表示从不越界)
#   breach_duration: 越界持续点数(需 >= 2 才能演示告警防抖/持续点数判定, 且应 < breach_cycle)
#   drift_enabled / drift_per_point / drift_cap: 线性漂移退化(预测性维护演示),
#       数值 = 正常期值 + min(drift_per_point*cycle, drift_cap - 正常期值), 叠加在正弦+噪声之上并封顶;
#       漂移开启时该传感器的越界逻辑被跳过(互斥, 原因见 in_breach 内注释)
#   noise_gain_enabled / noise_gain: 噪声增大退化(预测性维护演示), 正常期噪声幅度 ×noise_gain
SENSORS = [
    {   # 漂移退化主演示: 每点 +0.08(0.16°C/秒), 约 4 分钟漂过 75 兑现预测;
        # 漂移与周期越界互斥, 告警只来自漂移爬升越过 75 的时刻(即"预测兑现")
        "sensorCode": "TEMP-002",
        "equipmentCode": "EQ-002",
        "equipmentId": 2,
        "name": "温度传感器",
        "unit": "°C",
        "normal_min": 20.0,
        "normal_max": 60.0,
        "alert_upper": 75.0,
        "alert_lower": None,
        "breach_cycle": 0,
        "breach_duration": 0,
        "drift_enabled": True,
        "drift_per_point": 0.08,
        "drift_cap": 88.0,
    },
    {   # 噪声增大退化演示(MAD 突变/体制变化), 无规则仅触发 L2 检测
        "sensorCode": "VIB-002",
        "equipmentCode": "EQ-002",
        "equipmentId": 2,
        "name": "振动传感器",
        "unit": "mm/s",
        "normal_min": 0.0,
        "normal_max": 5.0,
        "alert_upper": None,
        "alert_lower": None,
        "breach_cycle": 0,
        "breach_duration": 0,
        "noise_gain_enabled": True,
        "noise_gain": 3.0,
    },
    {   # 正常对照组(不退化不越界, 供预测基线/前端对比展示)
        "sensorCode": "HUM-002",
        "equipmentCode": "EQ-002",
        "equipmentId": 2,
        "name": "湿度传感器",
        "unit": "%RH",
        "normal_min": 30.0,
        "normal_max": 70.0,
        "alert_upper": None,
        "alert_lower": None,
        "breach_cycle": 0,
        "breach_duration": 0,
    },
]

# 退化项运行时开关(sensorCode → 当前是否生效), 启动时从 SENSORS 静态配置初始化:
# 维护指令 reset 只清零这里的运行时状态、不回改 SENSORS,
# 因此维护后要再次开启退化演示需重启模拟器(可接受的演示语义)
drift_runtime = {cfg["sensorCode"]: cfg.get("drift_enabled", False) for cfg in SENSORS}
noise_gain_runtime = {cfg["sensorCode"]: cfg.get("noise_gain_enabled", False) for cfg in SENSORS}

# ============================== 数据生成 ==============================


def in_breach(cycle, cfg):
    """判断当前周期是否处于越界窗口: cycle % breach_cycle == 0 起连续 breach_duration 个点"""
    # 漂移与越界互斥: 漂移生效期间必须跳过越界——
    # 周期性突发越界会污染预测侧的回归拟合与偏差回溯, 干扰退化趋势判定
    if drift_runtime.get(cfg["sensorCode"], False):
        return False
    breach_cycle = cfg["breach_cycle"]
    duration = cfg["breach_duration"]
    if not breach_cycle or not duration:
        return False
    # 两个方向的阈值都没配, 则无从越界
    if cfg["alert_upper"] is None and cfg["alert_lower"] is None:
        return False
    return cycle % breach_cycle < duration


def gen_value(cycle, cfg, stable_only=False):
    """生成一个数据点, 返回 (值, 标记列表)

    正常期: 正弦波在 [normal_min, normal_max] 内波动 + 随机噪声(量程 ±5%);
    越界期: 有上限阈值则冲高(超出阈值 10%~20%), 否则用下限阈值下探(低于阈值 10%~20%);
    退化项: 漂移/噪声增大按运行时开关叠加在正常期数值之上(漂移开启时越界被互斥跳过);
    stable_only=True: 只生成平稳期数值(跳过越界与全部退化项), 供 --backfill 回填历史数据用。
    """
    if not stable_only and in_breach(cycle, cfg):
        if cfg["alert_upper"] is not None:
            # 有上限优先向上冲高
            base = cfg["alert_upper"]
            return base * (1.0 + random.uniform(0.10, 0.20)), ["越界"]
        # 无上限则向下探低(基于阈值绝对值, 供负/零下限时仍能产生偏移)
        base = abs(cfg["alert_lower"])
        return cfg["alert_lower"] - base * random.uniform(0.10, 0.20), ["越界"]

    code = cfg["sensorCode"]
    mid = (cfg["normal_min"] + cfg["normal_max"]) / 2.0
    amp = (cfg["normal_max"] - cfg["normal_min"]) / 2.0
    value = mid + amp * math.sin(2.0 * math.pi * cycle / SINE_PERIOD_CYCLES)

    # 随机噪声模拟真实采集抖动, 幅度为量程的 ±5%; 噪声增大退化生效时幅度 ×noise_gain
    noise_gain_on = not stable_only and noise_gain_runtime.get(code, False)
    noise_scale = cfg["noise_gain"] if noise_gain_on else 1.0
    value += random.uniform(-0.05, 0.05) * (cfg["normal_max"] - cfg["normal_min"]) * noise_scale

    # 线性漂移退化: 叠加在正弦+噪声之上, 累计漂移量封顶 drift_cap
    drift_on = not stable_only and drift_runtime.get(code, False)
    if drift_on:
        value += min(cfg["drift_per_point"] * cycle, cfg["drift_cap"] - value)

    # 退化标记供发布日志展示(沿用 [越界] 标记风格)
    markers = []
    if noise_gain_on:
        markers.append("噪声增大")
    if drift_on:
        markers.append("漂移")
    return value, markers

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
        # 订阅维护指令主题 maintenance/{equipmentCode}(通配所有设备):
        # 放在 on_connect 里, 断线重连成功后订阅自动恢复
        client.subscribe("maintenance/#")
        print(f"[{now()}] [MQTT] 已连接 {BROKER_HOST}:{BROKER_PORT}")
    else:
        print(f"[{now()}] [MQTT] 连接失败 rc={rc}")


def on_disconnect(client, userdata, rc):
    print(f"[{now()}] [MQTT] 连接断开 rc={rc}, 等待自动重连...")


def on_message(client, userdata, msg):
    """维护指令回调: maintenance/{equipmentCode} 且 payload JSON 含 action=reset 时,
    清零该设备全部传感器的退化运行时开关(不修改 SENSORS 静态配置)"""
    # 只处理 maintenance/{equipmentCode} 两级 topic, 更深层级或无关消息直接忽略
    parts = msg.topic.split("/")
    if len(parts) != 2 or parts[0] != "maintenance":
        return
    try:
        payload = json.loads(msg.payload.decode("utf-8"))
    except (ValueError, UnicodeDecodeError):
        print(f"[{now()}] [维护] 忽略无法解析的指令: topic={msg.topic}")
        return
    # payload 必须是 JSON 对象且 action 为 reset, 其余指令不处理
    if not isinstance(payload, dict) or payload.get("action") != "reset":
        return
    equipment_code = parts[1]
    # 运行时开关清零后不会自动恢复(不回改 SENSORS), 再次开启退化演示需重启模拟器
    reset_sensors = []
    for cfg in SENSORS:
        code = cfg["sensorCode"]
        if cfg["equipmentCode"] == equipment_code and (drift_runtime[code] or noise_gain_runtime[code]):
            drift_runtime[code] = False
            noise_gain_runtime[code] = False
            reset_sensors.append(code)
    reset_desc = ", ".join(reset_sensors) if reset_sensors else "无生效中的退化项"
    print(f"[{now()}] [维护] 设备 {equipment_code} 退化效果已重置({reset_desc}; 再次开启需重启模拟器)")


def build_client():
    """client_id 带 uuid 后缀避免多实例冲突; 兼容 paho-mqtt 1.x / 2.x 两种构造签名"""
    client_id = f"sensor-simulator-predict-{uuid.uuid4().hex[:8]}"
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
        value, markers = gen_value(cycle, cfg)

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
        # 越界/退化标记拼接在日志尾部(如 [越界]/[漂移]/[噪声增大])
        marker_text = "".join(f" [{m}]" for m in markers)
        print(f"[{now()}] [PUB] {topic} {cfg['name']}={round(value, 2)}{cfg['unit']}"
              f"{marker_text} (cycle={cycle})")


def backfill_round(client, round_no, ts):
    """回填一轮: 只发平稳期数值(跳过越界与退化项), 用指定时间戳, 不逐点打印

    payload 结构与 publish_round 完全一致; 正弦相位由回填轮次驱动,
    不推进全局 cycle_counters——否则实时阶段的漂移会按累计轮数起算, 一出场就封顶。
    """
    for cfg in SENSORS:
        value, _ = gen_value(round_no, cfg, stable_only=True)
        topic = f"sensor/{cfg['equipmentCode']}/{cfg['sensorCode']}"
        payload = json.dumps({
            "sensorCode": cfg["sensorCode"],
            "sensorValue": round(value, 2),
            "equipmentId": cfg["equipmentId"],
            "timestamp": ts.isoformat(),
        }, ensure_ascii=False)
        info = client.publish(topic, payload)
        if info.rc != mqtt.MQTT_ERR_SUCCESS:
            print(f"[{now()}] [PUB] 发布失败 rc={info.rc}: {topic}")


def backfill(client, minutes):
    """回填历史数据: 按轮次快速重放 minutes 分钟的平稳期数据(不含越界与退化项)

    每轮时间戳 = now - (总轮次-当前轮次)*PUBLISH_INTERVAL_SECONDS, 逆推铺满 N 分钟,
    使历史数据与回填结束后的实时发布在时间轴上衔接; 每轮 sleep BACKFILL_ROUND_SLEEP
    控制灌入速度, 期间每 2000 轮打印一行进度。
    """
    total_rounds = int(minutes * 60 / PUBLISH_INTERVAL_SECONDS)
    print(f"[{now()}] [回填] 开始回填 {minutes} 分钟平稳期历史数据, 共 {total_rounds} 轮(不含退化项)")
    for i in range(1, total_rounds + 1):
        ts = datetime.now() - timedelta(seconds=(total_rounds - i) * PUBLISH_INTERVAL_SECONDS)
        backfill_round(client, i, ts)
        if i % 2000 == 0:
            print(f"[{now()}] [回填] 进度 {i}/{total_rounds} 轮")
        time.sleep(BACKFILL_ROUND_SLEEP)
    print(f"[{now()}] [回填] 完成, 进入正常实时发布(退化项按各自开关生效)")


def publish_loop(client, backfill_minutes=0):
    # 等首次连接成功再开始发布, 避免连接前的消息被丢弃
    connected.wait()
    if backfill_minutes > 0:
        backfill(client, backfill_minutes)
    while True:
        publish_round(client)
        time.sleep(PUBLISH_INTERVAL_SECONDS)


def main():
    # --backfill N: 启动时先回填 N 分钟平稳期历史数据(默认 0 不回填)
    parser = argparse.ArgumentParser(description="传感器数据模拟器——预测告警演示版(二号机床 EQ-002)")
    parser.add_argument("--backfill", type=int, default=0, metavar="N",
                        help="先回填 N 分钟平稳期历史数据, 再进入实时发布(默认 0 不回填; 预测演示建议 30)")
    args = parser.parse_args()
    if args.backfill < 0:
        parser.error("--backfill 不能为负数")

    # 预测链路前提提示: predict.enabled 未开启时 PredictTask 空转, 不会有 PREDICT 告警
    print(f"[{now()}] [提示] 本模拟器演示二号机床 EQ-002 预测告警: "
          f"请确认 Nacos 中 predict.enabled=true 且已重启 ruoyi-alert, "
          f"alert_rule 已配置 TEMP-002 规则(上限 75)")
    if args.backfill <= 0:
        print(f"[{now()}] [提示] 未指定 --backfill: TEMP-002 无历史数据时预测任务需约 4 分钟攒满窗口才开检")

    client = build_client()
    client.on_connect = on_connect
    client.on_disconnect = on_disconnect
    # 维护指令回调: maintenance/{equipmentCode} payload {"action":"reset"}
    client.on_message = on_message
    # 断线后 1~60 秒指数退避自动重连
    client.reconnect_delay_set(1, 60)

    # 发布循环放守护线程, 主线程跑阻塞的网络循环
    threading.Thread(target=publish_loop, args=(client, args.backfill), daemon=True).start()
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
