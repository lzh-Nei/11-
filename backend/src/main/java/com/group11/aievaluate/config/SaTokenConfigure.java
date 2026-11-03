package com.group11.aievaluate.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 基础配置。
 *
 * <p>注册 SaInterceptor 后，Controller 上的 {@code @SaCheckLogin}、
 * {@code @SaCheckRole}、{@code @SaCheckPermission} 注解才会生效。</p>
 *
 * <p>这里没有做全局路由拦截：登录/注册/验证码接口必须匿名可访问，
 * 目前按作业要求只对文件上传接口加注解。后续如果需要“除白名单外全部登录”，
 * 可以在 SaInterceptor 构造器中传入
 * {@code handle -> SaRouter.match("/api/**").notMatch("/api/auth/**").check(r -> StpUtil.checkLogin())}。</p>
 */
@Configuration
public class SaTokenConfigure implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 默认构造器会开启注解鉴权
        registry.addInterceptor(new SaInterceptor()).addPathPatterns("/**");
    }
}
