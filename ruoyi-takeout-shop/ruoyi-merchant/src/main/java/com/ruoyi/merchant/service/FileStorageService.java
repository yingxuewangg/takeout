package com.ruoyi.merchant.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 统一文件存储服务接口（策略模式，与 PaymentService 同风格）。
 *
 * 一期实现 LocalFileStorageServiceImpl（本地存储，参考若依上传思路：
 * 格式/大小校验 → 按日期目录存储 → UUID 重命名 → 返回"名称+相对路径"）；
 * 后续迁移阿里云 OSS 时新增 OssFileStorageServiceImpl，只换上传实现，
 * 不动数据库字段、不动历史数据、不改前端代码（v2.5 图片存储方案）。
 *
 * 管理端（商家上传菜品图）与小程序端 /api/file/upload（T7 退款凭证、T8 留言图片）共用本服务。
 *
 * @author 阿婆干饭社
 */
public interface FileStorageService
{
    /**
     * 上传图片文件
     *
     * @param file 上传的文件
     * @return 相对路径（如 /profile/upload/2026/09/25/{uuid}.png），调用方直接入库，禁止存完整 URL
     */
    String upload(MultipartFile file);
}
