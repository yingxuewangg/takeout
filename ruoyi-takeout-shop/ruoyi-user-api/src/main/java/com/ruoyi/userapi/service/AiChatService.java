package com.ruoyi.userapi.service;

import java.util.List;
import com.ruoyi.userapi.domain.BizAiChatSession;
import com.ruoyi.userapi.domain.vo.AiChatVo;

/**
 * AI 智能问答服务接口（二期 RAG）。
 * 提问流程：问题向量化 → VectorStore 相似度检索 TopK → 连同在售菜品组装 system 提示词 →
 * ChatClient 生成回答；支持多轮（带最近 N 轮历史）；会话与消息落库。
 *
 * @author 阿婆干饭社
 */
public interface AiChatService
{
    /**
     * 提问（sessionNo 为空则新建会话）
     *
     * @return 回答 + 知识来源 + 会话标识
     */
    AiChatVo chat(Long memberId, String sessionNo, String question);

    /**
     * 我的会话列表（按最后活跃倒序）
     */
    List<BizAiChatSession> listSessions(Long memberId);

    /**
     * 会话消息（归属校验；按时间正序）
     */
    List<com.ruoyi.userapi.domain.BizAiChatMessage> listMessages(Long memberId, String sessionNo);

    /**
     * 新建会话（返回会话标识）
     */
    String createSession(Long memberId);
}
