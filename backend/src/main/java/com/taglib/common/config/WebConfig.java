package com.taglib.common.config;

import com.taglib.common.interceptor.LoginInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.util.Arrays;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final LoginInterceptor loginInterceptor;
    private final String[] allowedOrigins;

    public WebConfig(LoginInterceptor loginInterceptor,
                     @Value("${taglib.cors.allowed-origins:http://localhost:[*],http://127.0.0.1:[*]}")
                     String allowedOrigins) {
        this.loginInterceptor = loginInterceptor;
        this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/auth/register",
                        "/api/auth/logout"   // 会话过期时也保持登出幂等
                );
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 生产环境同域托管静态文件，不需要 CORS；这里仅放行开发期的 Vite 服务器。
        // 通过 taglib.cors.allowed-origins 覆盖（逗号分隔，支持 [*] 端口通配），勿使用通配符放行任意来源。
        registry.addMapping("/api/**")
                .allowedOriginPatterns(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * Vue 使用 history 模式时，前端路由（如 /resource/1）在后端没有对应文件，
     * 刷新会 404。这里把未命中静态文件、且不以 api/ 开头的 GET 请求回退到 index.html，
     * 交给前端路由处理；/api/** 仍由 Controller 优先匹配。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/", "classpath:/public/", "classpath:/META-INF/resources/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        if (resourcePath.startsWith("api/")) {
                            return null;
                        }
                        Resource index = new ClassPathResource("/static/index.html");
                        return index.exists() ? index : null;
                    }
                });
    }
}
