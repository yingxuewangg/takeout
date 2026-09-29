package com.ruoyi.merchant.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.merchant.service.FileStorageService;

/**
 * 管理端统一文件上传接口。
 * 与小程序端 /api/file/upload 共用 FileStorageService，校验与存储规则完全一致：
 * 格式白名单 jpg/jpeg/png/webp、单张 ≤ takeout.file.max-size（默认5MB）、
 * 按日期目录存储、UUID 重命名、返回"名称+相对路径"入库（不存域名/完整 URL）。
 *
 * @author 阿婆干饭社
 */
@RestController
@RequestMapping("/merchant/file")
public class MerchantFileController extends BaseController
{
    @Autowired
    private FileStorageService fileStorageService;

    /**
     * 图片上传（返回相对路径，前端 ImageUpload 组件以 fileName 入库）
     */
    @PostMapping("/upload")
    public AjaxResult upload(@RequestPart("file") MultipartFile file)
    {
        String path = fileStorageService.upload(file);
        AjaxResult ajax = AjaxResult.success();
        ajax.put("url", path);
        ajax.put("fileName", path);
        ajax.put("newFileName", com.ruoyi.common.utils.file.FileUtils.getName(path));
        return ajax;
    }
}
