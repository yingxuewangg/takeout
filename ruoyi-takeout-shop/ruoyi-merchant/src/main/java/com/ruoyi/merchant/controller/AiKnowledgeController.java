package com.ruoyi.merchant.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.merchant.domain.BizAiKnowledge;
import com.ruoyi.merchant.service.AiKnowledgeService;

/**
 * 管理端 AI 知识库：上传（txt/md/pdf/docx）→ 解析切片 → 向量化入库；列表/删除（同步删向量）/重新解析。
 *
 * @author 阿婆干饭社
 */
@RestController
@RequestMapping("/merchant/ai/knowledge")
public class AiKnowledgeController extends BaseController
{
    @Autowired
    private AiKnowledgeService aiKnowledgeService;

    /**
     * 知识库文档分页列表（含解析状态与切片数）
     */
    @PreAuthorize("@ss.hasPermi('merchant:ai:knowledge:list')")
    @GetMapping("/list")
    public TableDataInfo list(@RequestParam(required = false) String fileName,
            @RequestParam(required = false) Integer parseStatus,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize)
    {
        IPage<BizAiKnowledge> page = aiKnowledgeService.listKnowledge(fileName, parseStatus, pageNum, pageSize);
        TableDataInfo rsp = new TableDataInfo(page.getRecords(), page.getTotal());
        rsp.setCode(200);
        rsp.setMsg("查询成功");
        return rsp;
    }

    /**
     * 上传知识库文档（支持 txt/md/pdf/docx；上传后异步解析并向量化）
     */
    @PreAuthorize("@ss.hasPermi('merchant:ai:knowledge:upload')")
    @Log(title = "AI知识库上传", businessType = BusinessType.INSERT)
    @PostMapping("/upload")
    public AjaxResult upload(@RequestParam("file") MultipartFile file)
    {
        BizAiKnowledge knowledge = aiKnowledgeService.upload(file, SecurityUtils.getUsername());
        AjaxResult ajax = success();
        ajax.put("id", knowledge.getId());
        ajax.put("fileName", knowledge.getFileName());
        ajax.put("parseStatus", knowledge.getParseStatus());
        return ajax;
    }

    /**
     * 删除文档（同步删除其全部向量与物理文件）
     */
    @PreAuthorize("@ss.hasPermi('merchant:ai:knowledge:remove')")
    @Log(title = "AI知识库删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable Long id)
    {
        aiKnowledgeService.remove(id);
        return success();
    }

    /**
     * 重新解析（先删旧向量再重新切片入库）
     */
    @PreAuthorize("@ss.hasPermi('merchant:ai:knowledge:reparse')")
    @Log(title = "AI知识库重新解析", businessType = BusinessType.UPDATE)
    @PutMapping("/reparse/{id}")
    public AjaxResult reparse(@PathVariable Long id)
    {
        aiKnowledgeService.reparse(id);
        return success();
    }
}
