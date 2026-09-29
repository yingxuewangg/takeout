package com.ruoyi.merchant.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.merchant.service.MerchantCommentService;

/**
 * 管理端留言管理：查看（含是否关联订单及订单号）/回复/隐藏与恢复/删除。
 *
 * @author 阿婆干饭社
 */
@RestController
@RequestMapping("/merchant/comment")
public class MerchantCommentController extends BaseController
{
    @Autowired
    private MerchantCommentService merchantCommentService;

    /**
     * 留言分页列表（含隐藏；goodsName/hasOrder 筛选）
     */
    @PreAuthorize("@ss.hasPermi('merchant:comment:list')")
    @GetMapping("/list")
    public TableDataInfo list(@RequestParam(required = false) String goodsName,
            @RequestParam(required = false) Boolean hasOrder,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize)
    {
        IPage<MerchantCommentService.CommentManageVo> page =
                merchantCommentService.listComments(goodsName, hasOrder, pageNum, pageSize);
        TableDataInfo rsp = new TableDataInfo(page.getRecords(), page.getTotal());
        rsp.setCode(200);
        rsp.setMsg("查询成功");
        return rsp;
    }

    /**
     * 商家回复（可再次编辑覆盖）
     */
    @PreAuthorize("@ss.hasPermi('merchant:comment:reply')")
    @Log(title = "留言回复", businessType = BusinessType.UPDATE)
    @PutMapping("/reply/{id}")
    public AjaxResult reply(@PathVariable Long id, @RequestBody ReplyBody body)
    {
        merchantCommentService.reply(id, body.getReplyContent());
        return success();
    }

    /**
     * 隐藏违规留言（用户端不可见）
     */
    @PreAuthorize("@ss.hasPermi('merchant:comment:hide')")
    @Log(title = "留言隐藏", businessType = BusinessType.UPDATE)
    @PutMapping("/hide/{id}")
    public AjaxResult hide(@PathVariable Long id)
    {
        merchantCommentService.hide(id);
        return success();
    }

    /**
     * 恢复显示
     */
    @PreAuthorize("@ss.hasPermi('merchant:comment:hide')")
    @Log(title = "留言恢复", businessType = BusinessType.UPDATE)
    @PutMapping("/show/{id}")
    public AjaxResult show(@PathVariable Long id)
    {
        merchantCommentService.show(id);
        return success();
    }

    /**
     * 删除留言（物理删除）
     */
    @PreAuthorize("@ss.hasPermi('merchant:comment:remove')")
    @Log(title = "留言删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public AjaxResult remove(@PathVariable Long id)
    {
        merchantCommentService.remove(id);
        return success();
    }

    /** 回复请求体 */
    public static class ReplyBody
    {
        private String replyContent;

        public String getReplyContent() { return replyContent; }
        public void setReplyContent(String replyContent) { this.replyContent = replyContent; }
    }
}
