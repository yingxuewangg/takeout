package com.ruoyi.merchant.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 业务配置。
 *
 * 若依系统表仍走 framework/MyBatisConfig 的原生 MyBatis 工厂；
 * 该工厂已替换为 MybatisSqlSessionFactoryBean（MP 官方整合方式），
 * biz_ 业务表统一走 MyBatis-Plus（BaseMapper / 条件构造器 / 分页插件）。
 *
 * @author 阿婆干饭社
 */
@Configuration
public class MybatisPlusConfig
{
    /**
     * MyBatis-Plus 插件链：目前仅分页插件（biz 表物理分页）。
     * 该拦截器由 framework/MyBatisConfig 装配进 SqlSessionFactory。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor()
    {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
