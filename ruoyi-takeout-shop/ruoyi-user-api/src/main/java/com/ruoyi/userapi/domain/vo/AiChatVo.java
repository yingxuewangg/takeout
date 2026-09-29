package com.ruoyi.userapi.domain.vo;

import java.util.List;

/**
 * AI 问答结果 VO（二期 RAG）
 *
 * @author 阿婆干饭社
 */
public class AiChatVo
{
    /** 会话标识 */
    private String sessionNo;

    /** 助手回答 */
    private String answer;

    /** 命中的知识来源（便于前端展示"知识来源"，直观证明 RAG 生效） */
    private List<Reference> references;

    /** 本次回答耗时（毫秒） */
    private Long costMs;

    /** 知识来源片段 */
    public static class Reference
    {
        /** 来源文件名 */
        private String source;

        /** 片段内容（截断展示） */
        private String text;

        /** 相似度得分 */
        private Double score;

        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }

        public Double getScore() { return score; }
        public void setScore(Double score) { this.score = score; }
    }

    public String getSessionNo() { return sessionNo; }
    public void setSessionNo(String sessionNo) { this.sessionNo = sessionNo; }

    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }

    public List<Reference> getReferences() { return references; }
    public void setReferences(List<Reference> references) { this.references = references; }

    public Long getCostMs() { return costMs; }
    public void setCostMs(Long costMs) { this.costMs = costMs; }
}
