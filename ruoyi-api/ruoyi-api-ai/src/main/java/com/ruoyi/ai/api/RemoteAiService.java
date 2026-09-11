package com.ruoyi.ai.api;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import com.ruoyi.common.core.constant.SecurityConstants;
import com.ruoyi.common.core.constant.ServiceNameConstants;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.ai.api.domain.AiPredictRequestDTO;
import com.ruoyi.ai.api.domain.AiPredictResultDTO;

/**
 * AI推理服务
 * <p>
 * 不配 fallback/fallbackFactory：用户决策不做降级容错，
 * pdm-server 或 ruoyi-ai 不可用时调用方直接感知失败并自行决定处置策略，
 * 避免降级返回假数据掩盖推理链路故障。
 * </p>
 *
 * @author smartartisan
 */
@FeignClient(contextId = "remoteAiService", value = ServiceNameConstants.AI_SERVICE)
public interface RemoteAiService
{
    /**
     * 单传感器时序窗口异常检测与RUL预测（ruoyi-ai 代理转发 pdm-server）
     *
     * @param request 推理请求（传感器编码/类型/时序窗口/阈值/预测步长）
     * @param source 请求来源
     * @return 结果
     */
    @PostMapping("/inner/ai/predict")
    public R<AiPredictResultDTO> predict(@RequestBody AiPredictRequestDTO request, @RequestHeader(SecurityConstants.FROM_SOURCE) String source);
}
