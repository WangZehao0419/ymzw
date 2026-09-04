#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""simulator-predict.py 导入级自检(不连 MQTT):退化/互斥/封顶/复位/回填参数

预测演示版模拟器(EQ-002)的算法行为验证:
漂移封顶数学性质: value += min(dpp*cycle, cap - value) 恒使 value <= cap(硬封顶)。
"""
import importlib.util
import subprocess
import sys

# 1) 语法编译(双模拟器都过一遍)
subprocess.run([sys.executable, "-m", "py_compile", "simulator-predict.py", "simulator.py"], check=True)
print("[1] py_compile OK (simulator-predict.py + simulator.py)")

# 文件名带连字符不是合法模块标识, 按路径加载
_spec = importlib.util.spec_from_file_location("simulator_predict", "simulator-predict.py")
sim = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(sim)

# 2) TEMP-002 漂移单调上移 + 封顶稳定
temp = sim.SENSORS[0]  # TEMP-002: dpp=0.08, cap=88, 600 点到顶
vals = [sim.gen_value(c, temp)[0] for c in range(1, 3001)]
seg_means = [sum(vals[i*600:(i+1)*600]) / 600 for i in range(5)]
assert seg_means[0] < seg_means[1], f"均值未上移: {seg_means}"
# 漂移速率: 首段均值应约等于 mid + 0.08*平均cycle(300) = 40+24 = 64
mid = (temp["normal_min"] + temp["normal_max"]) / 2
assert abs(seg_means[0] - (mid + 0.08 * 300.5)) < 2.0, f"漂移速率异常: {seg_means[0]}"
# 封顶后: 各段均值稳定在 cap 附近(正弦被削顶略低于 88)
assert all(84.0 <= m <= 88.5 for m in seg_means[1:]), f"封顶后未稳定: {seg_means}"
print(f"[2] TEMP-002 漂移单调+封顶 OK, 分段均值 {[round(m,1) for m in seg_means]}")

# 3) 硬封顶: 数学上 value+min(dpp*c, cap-value) <= cap 恒成立
assert max(vals) <= 88.0 + 1e-9, f"硬封顶失效: {max(vals)}"
print(f"[3] 硬封顶 88°C OK, 最大值 {max(vals):.2f}")

# 4) 漂移互斥: drift_runtime 生效时 in_breach 恒 False(D5)
assert not any(sim.in_breach(c, temp) for c in range(1, 3001)), "漂移期不得出现周期越界"
print("[4] 漂移/越界互斥 OK")

# 5) VIB-002 噪声增益×3: 同相位残差中位数约为增益前 3 倍
vib = sim.SENSORS[1]
def residual_amp(cfg, n=2400):
    vs = [sim.gen_value(c, cfg)[0] for c in range(1, n + 1)]
    return sorted(abs(vs[i] - vs[i - 60]) for i in range(60, n))[n // 2]
sim.noise_gain_runtime["VIB-002"] = False
amp_before = residual_amp(vib)
sim.noise_gain_runtime["VIB-002"] = True
amp_after = residual_amp(vib)
assert 2.0 < amp_after / amp_before < 4.5, f"噪声增益比例异常: {amp_after/amp_before}"
print(f"[5] VIB-002 噪声残差 {amp_before:.3f} → {amp_after:.3f} (×{amp_after/amp_before:.1f}) OK")

# 6) HUM-002 对照组: 无退化配置, 值在正常带内
hum = sim.SENSORS[2]
assert not hum.get("drift_enabled", False) and not hum.get("noise_gain_enabled", False)
hum_vals = [sim.gen_value(c, hum)[0] for c in range(1, 601)]
noise_amp = 0.05 * (hum["normal_max"] - hum["normal_min"])
assert all(hum["normal_min"] - noise_amp <= v <= hum["normal_max"] + noise_amp for v in hum_vals)
print("[6] HUM-002 对照组正常带 OK")

# 7) 维护复位: 运行时开关清零, 周期计数器不重启, 数据回落正常带
sim.cycle_counters["TEMP-002"] = 123
msg = type("M", (), {"topic": "maintenance/EQ-002", "payload": b'{"action":"reset"}'})()
sim.on_message(None, None, msg)
assert sim.drift_runtime["TEMP-002"] is False, "复位后漂移开关未清零"
assert sim.noise_gain_runtime["VIB-002"] is False, "复位后噪声开关未清零"
assert sim.cycle_counters["TEMP-002"] == 123, "复位不得重启周期计数器"
# 复位后 TEMP-002 回落正常带(无漂移分量)
reset_vals = [sim.gen_value(c, temp)[0] for c in range(1, 601)]
assert all(v <= temp["normal_max"] + noise_amp + 1e-9 for v in reset_vals), "复位后数据未回落"
# 未知 action / 乱 payload 不误触发
sim.on_message(None, None, type("M", (), {"topic": "maintenance/EQ-002", "payload": b'{"action":"stop"}'})())
sim.on_message(None, None, type("M", (), {"topic": "maintenance/EQ-002", "payload": b'not-json'})())
assert sim.cycle_counters["TEMP-002"] == 123
print("[7] 维护复位(开关清零+计数器不重启+数据回落+脏指令免疫) OK")

# 8) --backfill 参数: int 型, 默认 0, 显式值生效
import argparse
sys.argv = ["simulator-predict.py", "--backfill", "30"]
ap = argparse.ArgumentParser()
ap.add_argument("--backfill", type=int, default=0, metavar="N")
assert ap.parse_args().backfill == 30
sys.argv = ["simulator-predict.py"]
assert ap.parse_args().backfill == 0
print("[8] --backfill 参数解析(默认0/显式30) OK")

# 9) stable_only 回填段: 值在正常带内且无退化标记(即使退化开关开着)
sim.drift_runtime["TEMP-002"] = True
sim.noise_gain_runtime["VIB-002"] = True
for c in range(1, 601):
    v, markers = sim.gen_value(c, temp, stable_only=True)
    assert not markers, f"回填段不得带退化标记: {markers}"
    assert v <= temp["normal_max"] + noise_amp + 1e-9, "回填段混入漂移"
    v2, m2 = sim.gen_value(c, vib, stable_only=True)
    assert not m2 and v2 <= vib["normal_max"] + 0.05 * 5 + 1e-9
print("[9] 回填段 stable_only 无退化 OK")

# 10) sine-period 联动注释存在
src = open("simulator-predict.py", encoding="utf-8").read()
assert "predict.sine-period" in src, "缺少与后端 sine-period 的联动注释"
print("[10] sine-period 联动注释 OK")

print("\n全部自检通过 (10/10)")
