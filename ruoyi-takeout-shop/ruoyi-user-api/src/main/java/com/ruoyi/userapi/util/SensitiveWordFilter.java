package com.ruoyi.userapi.util;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

/**
 * 敏感词过滤器（词库放配置文件、启动加载一次，方案 3.2/决策 6；不做管理端维护页面）。
 * 词库：classpath:sensitive/sensitive-words.txt（每行一词，# 注释）。
 * 实现：DFA 字典树（trie），多词同查一次遍历，避免逐词 contains 的 O(词数x文本长) 开销。
 *
 * @author 阿婆干饭社
 */
@Component
public class SensitiveWordFilter
{
    private static final Logger log = LoggerFactory.getLogger(SensitiveWordFilter.class);

    private static final String WORDS_PATH = "sensitive/sensitive-words.txt";

    /** DFA 字典树根节点 */
    private final TrieNode root = new TrieNode();

    /** 词库词条数 */
    private int wordCount = 0;

    /** 字典树节点 */
    private static class TrieNode
    {
        final Map<Character, TrieNode> children = new HashMap<>();
        boolean end = false;
    }

    @PostConstruct
    public void load()
    {
        try (InputStream in = new ClassPathResource(WORDS_PATH).getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                String word = line.trim();
                if (word.isEmpty() || word.startsWith("#"))
                {
                    continue;
                }
                insert(word);
            }
            log.info("[敏感词] 词库加载完成：{} 个词条（{}）", wordCount, WORDS_PATH);
        }
        catch (Exception e)
        {
            log.error("[敏感词] 词库加载失败（过滤将不生效，请检查 {}）", WORDS_PATH, e);
        }
    }

    /** 插入词条到字典树 */
    private void insert(String word)
    {
        TrieNode node = root;
        for (char c : word.toCharArray())
        {
            node = node.children.computeIfAbsent(c, k -> new TrieNode());
        }
        if (!node.end)
        {
            node.end = true;
            wordCount++;
        }
    }

    /**
     * 是否包含敏感词（留言内容发布前校验）
     */
    public boolean contains(String text)
    {
        if (text == null || text.isEmpty() || wordCount == 0)
        {
            return false;
        }
        int len = text.length();
        for (int i = 0; i < len; i++)
        {
            TrieNode node = root;
            int j = i;
            while (j < len)
            {
                node = node.children.get(text.charAt(j));
                if (node == null)
                {
                    break;
                }
                if (node.end)
                {
                    return true;
                }
                j++;
            }
        }
        return false;
    }

    /** 词库词条数（运维观测用） */
    public int getWordCount()
    {
        return wordCount;
    }
}
