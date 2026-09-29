package com.ruoyi.userapi.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.userapi.config.ApiMemberContext;
import com.ruoyi.userapi.service.ApiCommentService;

/**
 * 小程序端商品留言接口。
 * 列表游客可看（白名单）；发表强制登录且**仅"买过"该商品的用户可留言**（关联订单校验，用户 2026-09-25 变更）。
 *
 * @author 阿婆干饭社
 */
@RestController
@RequestMapping("/api/comment")
public class ApiCommentController extends BaseController
{
    @Autowired
    private ApiCommentService apiCommentService;

    /**
     * 商品留言分页列表（游客可看；仅正常状态）
     */
    @GetMapping("/list/{goodsId}")
    public TableDataInfo list(@PathVariable Long goodsId,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize)
    {
        IPage<ApiCommentService.CommentVo> page = apiCommentService.listByGoods(goodsId, pageNum, pageSize);
        TableDataInfo rsp = new TableDataInfo(page.getRecords(), page.getTotal());
        rsp.setCode(200);
        rsp.setMsg("查询成功");
        return rsp;
    }

    /**
     * 留言统计（正常条数+平均分）
     */
    @GetMapping("/stats/{goodsId}")
    public AjaxResult stats(@PathVariable Long goodsId)
    {
        return success(apiCommentService.statsByGoods(goodsId));
    }

    /**
     * 当前用户可关联该商品的订单（"买过"的合格订单）
     */
    @GetMapping("/eligible-orders/{goodsId}")
    public AjaxResult eligibleOrders(@PathVariable Long goodsId)
    {
        return success(apiCommentService.eligibleOrders(ApiMemberContext.requireMemberId(), goodsId));
    }

    /**
     * 发表留言（强制登录；已购资格 + 关联订单校验 + 敏感词拦截）
     *
     * @param body {goodsId, orderId(必填，合格订单), content(≤500), images(≤5张相对路径), score(1-5)}
     */
    @PostMapping
    public AjaxResult add(@RequestBody AddBody body)
    {
        apiCommentService.addComment(ApiMemberContext.requireMemberId(),
                body.getGoodsId(), body.getOrderId(), body.getContent(), body.getImages(), body.getScore());
        return success();
    }

    /** 发表请求体 */
    public static class AddBody
    {
        private Long goodsId;

        private Long orderId;

        private String content;

        private List<String> images;

        private Integer score;

        public Long getGoodsId() { return goodsId; }
        public void setGoodsId(Long goodsId) { this.goodsId = goodsId; }

        public Long getOrderId() { return orderId; }
        public void setOrderId(Long orderId) { this.orderId = orderId; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }

        public List<String> getImages() { return images; }
        public void setImages(List<String> images) { this.images = images; }

        public Integer getScore() { return score; }
        public void setScore(Integer score) { this.score = score; }
    }
}
