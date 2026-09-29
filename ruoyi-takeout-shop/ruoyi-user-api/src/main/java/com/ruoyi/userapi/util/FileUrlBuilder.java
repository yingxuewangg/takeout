package com.ruoyi.userapi.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 图片完整 URL 拼接器（v2.5 图片存储方案）。
 * 数据库只存"名称+相对路径"，/api 接口返回给小程序前在此统一拼好完整 URL：
 * 完整 URL = takeout.file.base-url + 相对路径；换域名/迁 OSS 只改配置，库与前端零改动。
 *
 * @author 阿婆干饭社
 */
@Component
public class FileUrlBuilder
{
    @Value("${takeout.file.base-url:}")
    private String baseUrl;

    /**
     * @param relativePath 数据库中的相对路径（可空）
     * @return 完整 URL；入参为空返回空串；已是完整 URL（历史数据兜底）原样返回
     */
    public String build(String relativePath)
    {
        if (!StringUtils.hasText(relativePath))
        {
            return "";
        }
        if (relativePath.startsWith("http://") || relativePath.startsWith("https://"))
        {
            return relativePath;
        }
        String prefix = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String path = relativePath.startsWith("/") ? relativePath : "/" + relativePath;
        return prefix + path;
    }
}
