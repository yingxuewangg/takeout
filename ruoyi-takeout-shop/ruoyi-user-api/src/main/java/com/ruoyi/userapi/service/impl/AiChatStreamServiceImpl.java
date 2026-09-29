package com.ruoyi.userapi.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.userapi.domain.BizAiChatMessage;
import com.ruoyi.userapi.domain.BizAiChatSession;
import com.ruoyi.userapi.domain.vo.AiChatVo;
import com.ruoyi.userapi.service.AiChatStreamService;

/**
 * AI 问答流式服务实现（二期 RAG 增强）。
 *
 * 复用 {@link AiChatServiceImpl} 的知识检索 / 提示词组装 / 消息落库能力（继承），
 * 区别：回答通过 **SSE 逐块推送**（打字机效果），且**知识来源不下发前端**——
 * 命中片段仅写入数据库（references_json）供服务端排障与验证，前端只看到回答正文。
 *
 * SSE 事件（data 为 JSON，便于小程序端统一解析）：
 *   {"type":"start","sessionNo":"..."}   开始（返回会话号，新建会话时前端需记录）
 *   {"type":"delta","content":"片段"}     增量内容
 *   {"type":"done","costMs":123}          正常结束
 *   {"type":"error","message":"..."}      异常
 *
 * @author 阿婆干饭社
 */
@Service("aiChatStreamServiceImpl")
public class AiChatStreamServiceImpl extends AiChatServiceImpl implements AiChatStreamService
{
    private static final Logger log = LoggerFactory.getLogger(AiChatStreamServiceImpl.class);

    /** SSE 超时：模型生成较慢，给足 3 分钟 */
    private static final long SSE_TIMEOUT_MS = 180_000L;

    @Override
    public SseEmitter streamChat(Long memberId, String sessionNo, String question)
    {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        // ---- 参数校验与会话准备（在请求线程完成，错误以 SSE error 事件返回，前端可统一处理）----
        final String q;
        final BizAiChatSession session;
        final List<BizAiChatMessage> history;
        final List<Document> hits;
        try
        {
            if (!StringUtils.hasText(question))
            {
                throw new ServiceException("请输入你的问题");
            }
            q = question.trim();
            if (q.length() > 500)
            {
                throw new ServiceException("问题过长，请控制在 500 字以内");
            }
            session = getOrCreateSession(memberId, sessionNo, q);
            history = messageMapper.selectList(new LambdaQueryWrapper<BizAiChatMessage>()
                    .eq(BizAiChatMessage::getSessionId, session.getId())
                    .orderByDesc(BizAiChatMessage::getId)
                    .last("limit " + historyRounds * 2));
            java.util.Collections.reverse(history);
            hits = retrieve(q);
        }
        catch (Exception e)
        {
            sendError(emitter, e.getMessage() == null ? "请求失败" : e.getMessage());
            return emitter;
        }

        // ---- 组装消息：system（角色约束 + 在售菜品 + 知识片段）+ 历史 + 当前问题 ----
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(buildSystemPrompt(hits)));
        for (BizAiChatMessage h : history)
        {
            if (BizAiChatMessage.ROLE_USER.equals(h.getRole()))
            {
                messages.add(new UserMessage(h.getContent()));
            }
            else if (BizAiChatMessage.ROLE_ASSISTANT.equals(h.getRole()))
            {
                messages.add(new AssistantMessage(h.getContent()));
            }
        }
        messages.add(new UserMessage(q));

        final long start = System.currentTimeMillis();
        final StringBuilder answerBuf = new StringBuilder();
        final AtomicBoolean finished = new AtomicBoolean(false);
        // 命中片段：仅用于落库留痕（不下发前端）
        final List<AiChatVo.Reference> references = toReferences(hits);

        // 先推送 start（携带会话号）
        try
        {
            emitter.send(SseEmitter.event().data(JSON.toJSONString(
                    Map.of("type", "start", "sessionNo", session.getSessionNo()))));
        }
        catch (Exception e)
        {
            log.warn("[AI流式] 发送 start 失败（客户端可能已断开）：{}", e.getMessage());
            return emitter;
        }

        // ---- 订阅模型流式输出 ----
        chatClient.prompt().messages(messages).stream().content().subscribe(
                chunk -> {
                    if (chunk == null || chunk.isEmpty())
                    {
                        return;
                    }
                    answerBuf.append(chunk);
                    try
                    {
                        emitter.send(SseEmitter.event().data(JSON.toJSONString(
                                Map.of("type", "delta", "content", chunk))));
                    }
                    catch (Exception e)
                    {
                        // 客户端断开：不再推送（落库仍会完成，保证数据一致）
                        log.warn("[AI流式] 推送增量失败（客户端已断开）：{}", e.getMessage());
                    }
                },
                error -> {
                    log.error("[AI流式] 模型调用失败：member={} question={}", memberId, q, error);
                    if (finished.compareAndSet(false, true))
                    {
                        String partial = answerBuf.toString();
                        persist(session, q, partial, references, start);
                        sendError(emitter, "AI 助手暂时无法回答，请稍后再试（" + shortMsgOf(error) + "）");
                    }
                },
                () -> {
                    if (finished.compareAndSet(false, true))
                    {
                        String answer = answerBuf.toString();
                        if (!StringUtils.hasText(answer))
                        {
                            answer = "抱歉，我没能组织出回答，请换个说法再问一次。";
                        }
                        long costMs = System.currentTimeMillis() - start;
                        persist(session, q, answer, references, start);
                        try
                        {
                            emitter.send(SseEmitter.event().data(JSON.toJSONString(
                                    Map.of("type", "done", "costMs", costMs))));
                        }
                        catch (Exception e)
                        {
                            log.warn("[AI流式] 发送 done 失败：{}", e.getMessage());
                        }
                        emitter.complete();
                        log.info("[AI流式] 完成：member={} session={} 命中片段={} 耗时={}ms 字数={}",
                                memberId, session.getSessionNo(), references.size(), costMs, answer.length());
                    }
                });

        emitter.onTimeout(() -> {
            log.warn("[AI流式] SSE 超时：session={}", session.getSessionNo());
            if (finished.compareAndSet(false, true))
            {
                persist(session, q, answerBuf.toString(), references, start);
            }
            emitter.complete();
        });
        emitter.onError(e -> log.warn("[AI流式] SSE 连接异常：{}", e.getMessage()));
        return emitter;
    }

    /** 落库：用户消息 + 助手消息（含命中片段，仅服务端留痕）+ 会话统计更新 */
    private void persist(BizAiChatSession session, String question, String answer,
            List<AiChatVo.Reference> references, long start)
    {
        try
        {
            long costMs = System.currentTimeMillis() - start;
            Date now = DateUtils.getNowDate();
            saveMessage(session.getId(), BizAiChatMessage.ROLE_USER, question, null, costMs, now);
            if (StringUtils.hasText(answer))
            {
                saveMessage(session.getId(), BizAiChatMessage.ROLE_ASSISTANT, answer,
                        references == null || references.isEmpty() ? null : JSON.toJSONString(references),
                        costMs, now);
            }
            BizAiChatSession update = new BizAiChatSession();
            update.setId(session.getId());
            update.setMessageCount((session.getMessageCount() == null ? 0 : session.getMessageCount()) + 2);
            update.setLastActiveTime(now);
            update.setUpdateTime(now);
            sessionMapper.updateById(update);
        }
        catch (Exception e)
        {
            log.error("[AI流式] 落库失败：session={}", session.getSessionNo(), e);
        }
    }

    private void sendError(SseEmitter emitter, String message)
    {
        try
        {
            emitter.send(SseEmitter.event().data(JSON.toJSONString(
                    Map.of("type", "error", "message", message))));
        }
        catch (Exception ignored)
        {
            // 客户端已断开，无需通知
        }
        emitter.complete();
    }

    private String shortMsgOf(Throwable e)
    {
        String m = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return m.length() > 120 ? m.substring(0, 120) + "..." : m;
    }
}
