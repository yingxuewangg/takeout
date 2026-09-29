package com.ruoyi.userapi.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.userapi.config.ApiMemberContext;
import com.ruoyi.userapi.domain.BizAiChatMessage;
import com.ruoyi.userapi.domain.BizAiChatSession;
import com.ruoyi.userapi.domain.vo.AiChatVo;
import com.ruoyi.userapi.service.AiChatService;
import com.ruoyi.userapi.service.AiChatStreamService;

/**
 * 小程序端 AI 智能问答（二期 RAG）：流式提问、会话列表、会话消息、新建会话。
 * 走小程序登录态（/api 拦截器），会话按用户隔离。
 *
 * @author 阿婆干饭社
 */
@RestController
@RequestMapping("/api/ai")
public class AiChatController extends BaseController
{
    @Autowired
    private AiChatService aiChatService;

    @Autowired
    private AiChatStreamService aiChatStreamService;

    /**
     * 流式提问（SSE，打字机效果）：前端用 uni.request + enableChunked 接收。
     * 用 GET 传参便于小程序端 onChunkReceived 简单处理；知识来源不下发前端。
     *
     * @param question  问题（≤500 字）
     * @param sessionNo 会话标识（可空，为空则新建）
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public SseEmitter chatStream(@RequestParam String question,
            @RequestParam(required = false) String sessionNo)
    {
        return aiChatStreamService.streamChat(ApiMemberContext.requireMemberId(), sessionNo, question);
    }

    /**
     * 提问（非流式，保留兼容：一次性返回完整回答）
     *
     * @param body {sessionNo: 可空, question: 问题}
     */
    @PostMapping("/chat")
    public AjaxResult chat(@RequestBody ChatBody body)
    {
        AiChatVo vo = aiChatService.chat(ApiMemberContext.requireMemberId(), body.getSessionNo(), body.getQuestion());
        return success(vo);
    }

    /**
     * 我的会话列表（按最后活跃倒序）
     */
    @GetMapping("/sessions")
    public AjaxResult sessions()
    {
        List<BizAiChatSession> list = aiChatService.listSessions(ApiMemberContext.requireMemberId());
        return success(list);
    }

    /**
     * 会话消息（归属校验，按时间正序）
     */
    @GetMapping("/session/{sessionNo}")
    public AjaxResult messages(@PathVariable String sessionNo)
    {
        List<BizAiChatMessage> list = aiChatService.listMessages(ApiMemberContext.requireMemberId(), sessionNo);
        return success(list);
    }

    /**
     * 新建会话（返回会话标识）
     */
    @PostMapping("/session/new")
    public AjaxResult newSession()
    {
        String sessionNo = aiChatService.createSession(ApiMemberContext.requireMemberId());
        AjaxResult ajax = success();
        ajax.put("sessionNo", sessionNo);
        return ajax;
    }

    /** 提问请求体 */
    public static class ChatBody
    {
        private String sessionNo;

        private String question;

        public String getSessionNo() { return sessionNo; }
        public void setSessionNo(String sessionNo) { this.sessionNo = sessionNo; }

        public String getQuestion() { return question; }
        public void setQuestion(String question) { this.question = question; }
    }
}
