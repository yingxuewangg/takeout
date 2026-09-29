package com.ruoyi.userapi.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.uuid.IdUtils;
import com.ruoyi.userapi.domain.BizAiChatMessage;
import com.ruoyi.userapi.domain.BizAiChatSession;
import com.ruoyi.userapi.domain.vo.AiChatVo;
import com.ruoyi.userapi.mapper.BizAiChatMessageMapper;
import com.ruoyi.userapi.mapper.BizAiChatSessionMapper;
import com.ruoyi.userapi.service.AiChatService;

/**
 * AI 智能问答服务实现（二期 RAG）。
 *
 * 提问链路（方案要求）：
 *   1. 取会话最近 N 轮历史（落库读取）
 *   2. 问题向量化 → VectorStore 相似度检索 TopK 片段（带阈值过滤）
 *   3. 组装 system 提示词：点餐助手角色 + 只依据知识库与在售菜品 + 健康建议给推荐与理由并注明仅供参考
 *      + 知识库没有的不编造；并把【当前在售菜品列表】实时注入
 *   4. ChatClient 生成回答（历史作为消息序列传入，支持多轮）
 *   5. 会话与消息落库（assistant 消息存命中片段引用，便于验证 RAG）
 *
 * @author 阿婆干饭社
 */
@Service("aiChatServiceImpl")
@org.springframework.context.annotation.Primary
public class AiChatServiceImpl implements AiChatService
{
    private static final Logger log = LoggerFactory.getLogger(AiChatServiceImpl.class);

    /** 单条知识片段在前端"知识来源"中的展示长度上限 */
    private static final int REFERENCE_TEXT_LIMIT = 160;

    @Value("${takeout.ai.top-k:4}")
    private int topK;

    @Value("${takeout.ai.similarity-threshold:0.35}")
    private double similarityThreshold;

    @Value("${takeout.ai.history-rounds:6}")
    protected int historyRounds;

    @Autowired
    protected ChatClient chatClient;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    protected BizAiChatSessionMapper sessionMapper;

    @Autowired
    protected BizAiChatMessageMapper messageMapper;

    /** 用于读取在售菜品（复用既有业务表，避免跨模块服务依赖） */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public AiChatVo chat(Long memberId, String sessionNo, String question)
    {
        if (!StringUtils.hasText(question))
        {
            throw new ServiceException("请输入你的问题");
        }
        String q = question.trim();
        if (q.length() > 500)
        {
            throw new ServiceException("问题过长，请控制在 500 字以内");
        }
        long start = System.currentTimeMillis();

        // 1. 会话：无则新建
        BizAiChatSession session = getOrCreateSession(memberId, sessionNo, q);

        // 2. 历史（最近 N 轮 = N*2 条，倒序取后正序使用）
        List<BizAiChatMessage> history = messageMapper.selectList(new LambdaQueryWrapper<BizAiChatMessage>()
                .eq(BizAiChatMessage::getSessionId, session.getId())
                .orderByDesc(BizAiChatMessage::getId)
                .last("limit " + (historyRounds * 2)));
        java.util.Collections.reverse(history);

        // 3. 知识检索（问题向量化 + TopK 相似度）
        List<Document> hits = retrieve(q);
        List<AiChatVo.Reference> references = toReferences(hits);

        // 4. 组装 messages：system（角色约束 + 在售菜品 + 知识片段）+ 历史 + 当前问题
        String systemPrompt = buildSystemPrompt(hits);
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
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

        // 5. 调模型生成回答
        String answer;
        try
        {
            answer = chatClient.prompt().messages(messages).call().content();
        }
        catch (Exception e)
        {
            log.error("[AI问答] 模型调用失败：member={} question={}", memberId, q, e);
            throw new ServiceException("AI 助手暂时无法回答，请稍后再试（" + shortMsg(e) + "）");
        }
        if (!StringUtils.hasText(answer))
        {
            answer = "抱歉，我没能组织出回答，请换个说法再问一次。";
        }
        long costMs = System.currentTimeMillis() - start;

        // 6. 落库：用户消息 + 助手消息（含命中片段引用）
        Date now = DateUtils.getNowDate();
        saveMessage(session.getId(), BizAiChatMessage.ROLE_USER, q, null, costMs, now);
        saveMessage(session.getId(), BizAiChatMessage.ROLE_ASSISTANT, answer,
                references.isEmpty() ? null : JSON.toJSONString(references), costMs, now);

        // 7. 会话统计更新
        BizAiChatSession sessionUpdate = new BizAiChatSession();
        sessionUpdate.setId(session.getId());
        sessionUpdate.setMessageCount((session.getMessageCount() == null ? 0 : session.getMessageCount()) + 2);
        sessionUpdate.setLastActiveTime(now);
        sessionUpdate.setUpdateTime(now);
        sessionMapper.updateById(sessionUpdate);

        AiChatVo vo = new AiChatVo();
        vo.setSessionNo(session.getSessionNo());
        vo.setAnswer(answer);
        vo.setReferences(references);
        vo.setCostMs(costMs);
        log.info("[AI问答] member={} session={} 命中知识片段={} 耗时={}ms",
                memberId, session.getSessionNo(), references.size(), costMs);
        return vo;
    }

    @Override
    public List<BizAiChatSession> listSessions(Long memberId)
    {
        return sessionMapper.selectList(new LambdaQueryWrapper<BizAiChatSession>()
                .eq(BizAiChatSession::getMemberId, memberId)
                .orderByDesc(BizAiChatSession::getLastActiveTime)
                .orderByDesc(BizAiChatSession::getId));
    }

    @Override
    public List<BizAiChatMessage> listMessages(Long memberId, String sessionNo)
    {
        BizAiChatSession session = getOwnedSession(memberId, sessionNo);
        return messageMapper.selectList(new LambdaQueryWrapper<BizAiChatMessage>()
                .eq(BizAiChatMessage::getSessionId, session.getId())
                .orderByAsc(BizAiChatMessage::getId));
    }

    @Override
    public String createSession(Long memberId)
    {
        BizAiChatSession session = newSession(memberId, "新的对话");
        sessionMapper.insert(session);
        return session.getSessionNo();
    }

    /** 会话获取或新建（sessionNo 为空则新建；非空必须属于本人） */
    protected BizAiChatSession getOrCreateSession(Long memberId, String sessionNo, String firstQuestion)
    {
        if (StringUtils.hasText(sessionNo))
        {
            BizAiChatSession session = getOwnedSession(memberId, sessionNo);
            // 首次提问时用问题生成标题
            if (!StringUtils.hasText(session.getTitle()) || "新的对话".equals(session.getTitle()))
            {
                BizAiChatSession update = new BizAiChatSession();
                update.setId(session.getId());
                update.setTitle(buildTitle(firstQuestion));
                sessionMapper.updateById(update);
                session.setTitle(update.getTitle());
            }
            return session;
        }
        BizAiChatSession session = newSession(memberId, buildTitle(firstQuestion));
        sessionMapper.insert(session);
        return session;
    }

    private BizAiChatSession newSession(Long memberId, String title)
    {
        Date now = DateUtils.getNowDate();
        BizAiChatSession session = new BizAiChatSession();
        session.setSessionNo(IdUtils.fastSimpleUUID());
        session.setMemberId(memberId);
        session.setTitle(title);
        session.setMessageCount(0);
        session.setLastActiveTime(now);
        session.setCreateTime(now);
        session.setUpdateTime(now);
        return session;
    }

    /** 归属校验：只能访问自己的会话 */
    protected BizAiChatSession getOwnedSession(Long memberId, String sessionNo)
    {
        BizAiChatSession session = sessionMapper.selectOne(new LambdaQueryWrapper<BizAiChatSession>()
                .eq(BizAiChatSession::getSessionNo, sessionNo).last("limit 1"));
        if (session == null || !session.getMemberId().equals(memberId))
        {
            throw new ServiceException("会话不存在");
        }
        return session;
    }

    protected String buildTitle(String question)
    {
        String t = question == null ? "新的对话" : question.trim();
        return t.length() > 20 ? t.substring(0, 20) : t;
    }

    /** 相似度检索 TopK（向量库未就绪/无数据时返回空列表，不影响问答与在售菜品推荐） */
    protected List<Document> retrieve(String question)
    {
        try
        {
            SearchRequest request = SearchRequest.builder()
                    .query(question)
                    .topK(topK)
                    .similarityThreshold(similarityThreshold)
                    .build();
            List<Document> docs = vectorStore.similaritySearch(request);
            return docs == null ? List.of() : docs;
        }
        catch (Exception e)
        {
            log.warn("[AI问答] 知识检索失败（将仅依据在售菜品回答）：{}", e.getMessage());
            return List.of();
        }
    }

    protected List<AiChatVo.Reference> toReferences(List<Document> hits)
    {
        List<AiChatVo.Reference> list = new ArrayList<>();
        for (Document d : hits)
        {
            AiChatVo.Reference r = new AiChatVo.Reference();
            Object fileName = d.getMetadata() == null ? null : d.getMetadata().get("fileName");
            r.setSource(fileName == null ? "知识库" : String.valueOf(fileName));
            String text = d.getText() == null ? "" : d.getText().trim();
            r.setText(text.length() > REFERENCE_TEXT_LIMIT ? text.substring(0, REFERENCE_TEXT_LIMIT) + "..." : text);
            r.setScore(d.getScore());
            list.add(r);
        }
        return list;
    }

    /**
     * 组装 system 提示词（方案要求）：
     * - 角色：餐饮店点餐助手
     * - 只依据知识库内容与在售菜品回答；知识库没有的信息不要编造
     * - 涉及健康建议时给出推荐菜品与理由，并提醒仅供参考
     * - 注入【当前在售菜品列表】（实时查库，保证推荐真实可下单）
     * - 注入【知识库检索片段】（RAG 检索结果）
     */
    protected String buildSystemPrompt(List<Document> hits)
    {
        StringBuilder sb = new StringBuilder();
        sb.append("你是「阿婆干饭社」的点餐助手，服务一家单店中餐馆。请遵守以下规则：\n");
        sb.append("1. 只依据下方【知识库内容】与【当前在售菜品】回答；知识库中没有的信息不要编造，")
          .append("如果不确定或没有相关内容，就直接说明暂时没有相关资料，并建议顾客联系商家。\n");
        sb.append("2. 涉及健康、忌口、营养等建议时（例如顾客说感冒了想吃清淡的），")
          .append("要结合知识库给出**具体推荐菜品和推荐理由**，并在结尾提醒：以上建议仅供参考，")
          .append("具体请遵医嘱或咨询专业人士。\n");
        sb.append("3. 推荐菜品时只能推荐【当前在售菜品】列表中真实存在的菜品，并带上价格，不要推荐不在售的商品。\n");
        sb.append("4. 回答要口语化、亲切、简洁，可用简短列表，不要输出 Markdown 表格。\n\n");

        sb.append("【当前在售菜品】\n");
        sb.append(listOnSaleGoods());
        sb.append("\n");

        sb.append("【知识库内容】\n");
        if (hits == null || hits.isEmpty())
        {
            sb.append("（本次未检索到相关知识库内容）\n");
        }
        else
        {
            int i = 1;
            for (Document d : hits)
            {
                sb.append("【片段").append(i++).append("】").append(d.getText() == null ? "" : d.getText().trim()).append("\n");
            }
        }
        return sb.toString();
    }

    /** 在售菜品列表（实时查库：名称 + 价格 + 是否售罄） */
    private String listOnSaleGoods()
    {
        try
        {
            List<java.util.Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "select g.name, g.price, g.sold_out, g.description, c.name as category_name "
                            + "from biz_goods g left join biz_category c on c.id = g.category_id "
                            + "where g.status = '1' and c.status = '0' order by c.sort asc, g.sort asc, g.id asc");
            if (rows.isEmpty())
            {
                return "（当前无可售菜品）\n";
            }
            StringBuilder sb = new StringBuilder();
            for (java.util.Map<String, Object> row : rows)
            {
                Object soldOut = row.get("sold_out");
                String status = "1".equals(String.valueOf(soldOut)) ? "（今日已售罄）" : "";
                Object desc = row.get("description");
                sb.append("- ").append(row.get("name"))
                  .append(" ¥").append(formatPrice(row.get("price")));
                if (row.get("category_name") != null)
                {
                    sb.append("［").append(row.get("category_name")).append("］");
                }
                if (desc != null && StringUtils.hasText(String.valueOf(desc)))
                {
                    sb.append("：").append(desc);
                }
                sb.append(status).append("\n");
            }
            return sb.toString();
        }
        catch (Exception e)
        {
            log.warn("[AI问答] 在售菜品查询失败：{}", e.getMessage());
            return "（菜品信息暂时不可用）\n";
        }
    }

    private String formatPrice(Object price)
    {
        if (price == null)
        {
            return "0.00";
        }
        if (price instanceof BigDecimal)
        {
            return ((BigDecimal) price).toPlainString();
        }
        return String.valueOf(price);
    }

    protected void saveMessage(Long sessionId, String role, String content, String referencesJson, Long costMs, Date now)
    {
        BizAiChatMessage msg = new BizAiChatMessage();
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setReferencesJson(referencesJson);
        msg.setCostMs(costMs);
        msg.setCreateTime(now);
        messageMapper.insert(msg);
    }

    protected String shortMsg(Exception e)
    {
        String m = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return m.length() > 120 ? m.substring(0, 120) + "..." : m;
    }
}
