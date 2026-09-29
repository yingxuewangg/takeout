package com.ruoyi.merchant.domain;

import java.io.Serializable;
import java.util.Date;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 菜品分类对象 biz_category
 *
 * @author 阿婆干饭社
 */
@TableName("biz_category")
public class BizCategory implements Serializable
{
    private static final long serialVersionUID = 1L;

    /** 分类ID */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 分类名称 */
    private String name;

    /** 显示顺序（越小越靠前） */
    private Integer sort;

    /** 状态（0正常 1停用） */
    private String status;

    private String createBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    private String updateBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getSort() { return sort; }
    public void setSort(Integer sort) { this.sort = sort; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCreateBy() { return createBy; }
    public void setCreateBy(String createBy) { this.createBy = createBy; }

    public Date getCreateTime() { return createTime; }
    public void setCreateTime(Date createTime) { this.createTime = createTime; }

    public String getUpdateBy() { return updateBy; }
    public void setUpdateBy(String updateBy) { this.updateBy = updateBy; }

    public Date getUpdateTime() { return updateTime; }
    public void setUpdateTime(Date updateTime) { this.updateTime = updateTime; }
}
