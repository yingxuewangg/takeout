package com.ruoyi.userapi.controller;

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
 * 小程序端统一文件上传接口（/api 前缀，走小程序登录态）。
 * 与管理端 /merchant/file/upload 共用 FileStorageService，校验与存储规则完全一致。
 * 供 T7（退款凭证）与 T8（留言图片）复用；返回相对路径，由调用方入库。
 *
 * 注意：登录态拦截器随 T3（微信登录）实现后统一覆盖 /api/**；
 * 本接口交付时暂不校验登录态（与方案"统一文件上传模块随 T2 交付"一致）。
 *
 * @author 阿婆干饭社
 */
@RestController
@RequestMapping("/api/file")
public class FileController extends BaseController
{
    @Autowired
    private FileStorageService fileStorageService;

    /**
     * 图片上传（multipart，字段名 file）
     *
     * @return fileName：相对路径（如 /profile/upload/2026/09/25/{uuid}.png），入库只存相对路径
     */
    @PostMapping("/upload")
    public AjaxResult upload(@RequestPart("file") MultipartFile file)
    {
        String path = fileStorageService.upload(file);
        AjaxResult ajax = AjaxResult.success();
        ajax.put("fileName", path);
        return ajax;
    }
}
