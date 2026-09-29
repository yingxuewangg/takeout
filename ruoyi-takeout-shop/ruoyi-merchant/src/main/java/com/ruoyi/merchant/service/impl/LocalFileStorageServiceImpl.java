package com.ruoyi.merchant.service.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.file.FileUploadUtils;
import com.ruoyi.merchant.service.FileStorageService;

/**
 * 本地文件存储实现（一期）。
 * 存储根目录为若依 ruoyi.profile 配置的 upload 子目录；格式白名单与单文件大小上限
 * 取自 takeout.file.allow-extensions / takeout.file.max-size 配置（默认 jpg/jpeg/png/webp、5MB）。
 * 文件名 UUID 重命名，按日期目录存储，返回相对路径 /profile/upload/yyyy/MM/dd/{uuid}.{ext}。
 *
 * @author 阿婆干饭社
 */
@Service
public class LocalFileStorageServiceImpl implements FileStorageService
{
    /** 上传格式白名单（逗号分隔） */
    @Value("${takeout.file.allow-extensions:jpg,jpeg,png,webp}")
    private String allowExtensions;

    /** 单文件大小上限（如 5MB） */
    @Value("${takeout.file.max-size:5MB}")
    private DataSize maxSize;

    @Override
    public String upload(MultipartFile file)
    {
        if (file == null || file.isEmpty())
        {
            throw new ServiceException("上传文件不能为空");
        }
        if (file.getSize() > maxSize.toBytes())
        {
            throw new ServiceException("上传文件大小不能超过 " + maxSize.toMegabytes() + "MB");
        }
        String[] extensions = allowExtensions.split(",");
        try
        {
            // 格式白名单校验（FileUploadUtils 内含扩展名黑名单与魔法值校验）
            FileUploadUtils.assertAllowed(file, extensions);
            // 日期目录 + UUID 重命名（避免中文乱码/重名覆盖/路径穿越），返回相对路径
            return FileUploadUtils.upload(RuoYiConfig.getUploadPath(), file, extensions, true);
        }
        catch (Exception e)
        {
            throw new ServiceException(e.getMessage());
        }
    }
}
