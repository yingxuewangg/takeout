package com.ruoyi.userapi.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 小程序端 Web MVC 配置：注册 /api 登录态拦截器。
 *
 * 放行名单（游客可浏览，后续任务如新增公开接口在此追加）：
 * - /api/login 登录本身
 * - /api/shop/info 店铺信息
 * - /api/menu/list 菜单列表
 * - /api/goods/** 商品搜索/详情（售罄可看详情）
 * - /api/goods/stock/** 今日剩余库存（T12；游客可看，与菜单浏览一致）
 * 其余 /api/** 一律要求登录（T4 下单等交易接口默认受保护）。
 *
 * @author 阿婆干饭社
 */
@Configuration
public class ApiWebConfig implements WebMvcConfigurer
{
    @Autowired
    private ApiLoginInterceptor apiLoginInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry)
    {
        registry.addInterceptor(apiLoginInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/login",
                        "/api/shop/info",
                        "/api/menu/list",
                        "/api/goods/**",
                        // 留言列表/统计游客可看；发表(/api/comment POST)与 eligible-orders 仍需登录
                        "/api/comment/list/**",
                        "/api/comment/stats/**");
    }
}
