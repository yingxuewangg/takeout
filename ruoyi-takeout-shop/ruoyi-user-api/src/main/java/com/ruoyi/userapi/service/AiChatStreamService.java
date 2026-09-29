package com.ruoyi.userapi.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 问答流式服务接口（二期 RAG 增强）。
 * 与 {@link AiChatService#chat} 的区别：回答以 SSE 逐块推送（打字机效果），
 * 前端不再需要"思考中"占位；知识来源仅在服务端留痕（落库），不下发前端。
 *
 * @author 阿婆干饭社
 */
public interface AiChatStreamService
{
    /**
     * 流式提问：检索知识 → 组装提示词 → 逐块推送模型输出 → 结束后落库会话与消息。
     * SSE 事件约定（data 为 JSON，便于小程序端解析）：
     *   {"type":"start","sessionNo":"..."}  开始（含会话号）
     *   {"type":"delta","content":"片段"}    增量内容
     *   {"type":"done","costMs":1234}        结束
     *   {"type":"error","message":"..."}     异常
     *
     * @param memberId  当前登录用户
     * @param sessionNo 会话标识（可空，为空则新建）
     * @param question  用户问题
     */
    SseEmitter streamChat(Long memberId, String sessionNo, String question);
}
