#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Chronos-2 纯模型服务:官方 predict_df 的 HTTP 封装,无任何业务逻辑。

定位(用户决策):Python 只做"模型即服务"——调用方(ruoyi-ai)传入模型推理
所需数据(窗口/步长/采样间隔),服务返回模型原始输出(三分位带,物理量纲);
评分/异常判定/健康分/RUL 等全部业务在 Java 侧(PdmScoringService)完成,
本服务不认识传感器、不认识阈值、无状态(同窗口输入永远同输出)。

内部仅三步:z-score 归一化(模型输入尺度稳定,服务内闭环,调用方透明)
→ predict_df(官方 API) → 反归一化回物理量纲。

错误约定:校验类错误抛 ValueError(HTTP 层转 400),模型输出异常抛 RuntimeError(500)。
"""

import os
import threading
import time

import numpy as np
import pandas as pd
import torch
from chronos import Chronos2Pipeline

# ============================== 模型可用性常量(非业务,联验调优在 Java 侧) ==============================
# horizon 上限:Chronos-2 单次前向最多 1024,256 远超演示需求,防误传大值拖垮服务
HORIZON_MAX = 256
# 窗口最小长度:模型上下文太短预测无意义(holdout 切分等业务约束由调用方校验)
MIN_CONTEXT = 16

# 运行配置:仅模型名(host/port 由 main.py 读)
MODEL_NAME = os.getenv("PDM_MODEL_NAME", "amazon/chronos-2")

# torch pipeline 非线程安全,而 FastAPI 的 sync 端点跑在线程池里:全局锁串行化推理
_LOCK = threading.Lock()

_pipeline = None  # 模块级单例:权重数 GB,进程内只加载一份


def get_pipeline():
    """懒加载模型单例;供启动预热(main.py lifespan)与推理共用。"""
    global _pipeline
    if _pipeline is None:
        _pipeline = Chronos2Pipeline.from_pretrained(MODEL_NAME, device_map=device())
    return _pipeline


def device() -> str:
    """推理设备标识(healthz 用;与 get_pipeline 选卡同一判定)"""
    return "cuda" if torch.cuda.is_available() else "cpu"


def _quantile_column(pred_df, level):
    """取 predict_df 分位列(官方输出列名恒为 str(quantile_level),见 chronos2/pipeline.py)"""
    key = str(level)
    if key in pred_df.columns:
        return pred_df[key].to_numpy(dtype=float)
    raise RuntimeError(f"predict_df 输出缺少分位列 {key},实际列: {list(pred_df.columns)}")


def predict(window, horizon, interval_seconds):
    """纯模型预测:窗口 → z-score 归一化 → predict_df → 反归一化,返回三分位带(物理量纲)。

    调用方负责窗口的业务切分(如 holdout 用前段做上下文),本函数只透传模型输出。
    """
    # ---- 校验(模型可用性,无业务语义) ----
    w = np.asarray(window, dtype=float)
    if w.size == 0 or not np.all(np.isfinite(w)):
        raise ValueError("window 为空或含 NaN/Inf,请检查上游数据链路")
    if w.size < MIN_CONTEXT:
        raise ValueError(f"window 长度 {w.size} 不足(需 ≥ {MIN_CONTEXT})")
    if not (1 <= horizon <= HORIZON_MAX):
        raise ValueError(f"horizon 需在 1..{HORIZON_MAX},实际 {horizon}")
    if interval_seconds is None or interval_seconds <= 0:
        raise ValueError(f"intervalSeconds 须 > 0,实际 {interval_seconds}")

    # ---- z-score 归一化(模型输入尺度稳定;恒值窗口防除零) ----
    mean = float(np.mean(w))
    std = float(np.std(w))
    if std <= 0.0:
        wn = np.zeros_like(w)
    else:
        wn = (w - mean) / std

    # ---- 官方 API:构造 DataFrame → predict_df ----
    # 合成等间隔时间戳即可:Chronos-2 只利用相对间隔,基准时刻不影响预测
    ctx = pd.DataFrame({
        "item_id": ["s"] * w.size,
        "timestamp": pd.date_range(pd.Timestamp("2026-01-01"), periods=w.size,
                                   freq=pd.Timedelta(seconds=interval_seconds)),
        "target": wn,
    })
    with _LOCK:
        # 计时起点在锁内:排除并发排队等待,inferenceMs 只反映单次推理真实耗时
        t0 = time.perf_counter()
        pred = get_pipeline().predict_df(ctx, prediction_length=horizon,
                                         quantile_levels=[0.1, 0.5, 0.9])
        q10 = _quantile_column(pred, 0.1) * std + mean  # 反归一化:调用方拿物理量纲
        q50 = _quantile_column(pred, 0.5) * std + mean
        q90 = _quantile_column(pred, 0.9) * std + mean
        inference_ms = int((time.perf_counter() - t0) * 1000)

    return {
        "q10": [round(float(v), 6) for v in q10],
        "q50": [round(float(v), 6) for v in q50],
        "q90": [round(float(v), 6) for v in q90],
        "modelVersion": MODEL_NAME,
        "inferenceMs": inference_ms,
    }
