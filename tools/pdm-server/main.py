#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Chronos-2 纯模型服务:FastAPI 入口,只做路由与契约,推理在 inference.py。

定位(用户决策):Python 侧只做"模型即服务"——传数据进来,返回模型结果;
所有业务(评分/异常判定/健康分/RUL)在 Java ruoyi-ai(PdmScoringService)完成。
本服务无传感器概念、无阈值参数、无状态。

启动方式(推荐):
    cd tools/pdm-server && uvicorn main:app --host 0.0.0.0 --port 8900
    或直接 python main.py(PDM_HOST/PDM_PORT 覆盖监听,默认 0.0.0.0:8900;
    PDM_MODEL_NAME 覆盖默认模型 amazon/chronos-2,如指向本地模型目录)

接口:GET /healthz → {"status", "model", "device"};POST /predict(契约见下方模型定义)。
"""

import logging
import os
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

import inference

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s [pdm-server] %(message)s")
log = logging.getLogger("pdm-server")

HOST = os.getenv("PDM_HOST", "0.0.0.0")
PORT = int(os.getenv("PDM_PORT", "8900"))


@asynccontextmanager
async def lifespan(app: FastAPI):
    # 启动期预热模型:问题在启动时暴露,而不是首个请求挂 500
    inference.get_pipeline()
    log.info("模型加载完成(model=%s, device=%s)", inference.MODEL_NAME, inference.device())
    yield


app = FastAPI(title="pdm-server", version="3.0", lifespan=lifespan)


class PredictRequest(BaseModel):
    window: list[float]          # 上下文序列(升序,最新在末尾;长度 ≥16)
    horizon: int                 # 预测步数(1..256)
    intervalSeconds: float       # 采样间隔(秒;模型时间戳构造用)


class PredictResponse(BaseModel):
    q10: list[float]             # 三分位预测序列(物理量纲,horizon 步)
    q50: list[float]
    q90: list[float]
    modelVersion: str
    inferenceMs: int


@app.get("/healthz")
def healthz():
    return {"status": "ok", "model": inference.MODEL_NAME, "device": inference.device()}


@app.post("/predict", response_model=PredictResponse)
def predict(req: PredictRequest):
    try:
        result = inference.predict(req.window, req.horizon, req.intervalSeconds)
    except ValueError as e:
        # 校验类错误:调用方可修(补窗口/传间隔),400 带明确原因
        raise HTTPException(400, str(e))
    except RuntimeError as e:
        # 模型输出异常等内部错误:500 带明确 message 便于定位
        raise HTTPException(500, f"推理失败: {e}")
    return PredictResponse(**result)


if __name__ == "__main__":
    # 直启入口:uvicorn 命令行仍是推荐方式,这里便于无 uvicorn 经验时一把跑起
    import uvicorn

    uvicorn.run(app, host=HOST, port=PORT)
