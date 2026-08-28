package com.ruoyi.inspection.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * AI服务Feign客户端
 * <p>
 * 用于远程调用cloud-ai服务的AI对话接口
 * 通过 OpenFeign 调用 cloud-ai 微服务，实现智能零件检测
 * </p>
 *
 * @author smartartisan
 */
@FeignClient(name = "cloud-ai", contextId = "aiFeignClient")
public interface AiFeignClient {

    /**
     * 调用AI服务进行对话
     * <p>
     * 使用默认模型进行对话，返回AI生成的回复
     * 返回的Map包含：success、message、response等字段
     * </p>
     *
     * @param message  用户消息内容
     * @return AI响应结果Map
     */
    @PostMapping("/ai/dynamic/part-inspection")
    Map<String, Object> chat(@RequestParam("message") String message);
}
