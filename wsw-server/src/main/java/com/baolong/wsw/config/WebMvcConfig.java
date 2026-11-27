package com.baolong.wsw.config;

import com.baolong.wsw.security.AuthInterceptor;
import com.baolong.wsw.security.IdempotentInterceptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 层配置：跨域、拦截器、上传文件静态映射。
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final IdempotentInterceptor idempotentInterceptor;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 本项目的鉴权靠 Authorization 头，不靠 Cookie，
        // 所以不需要 allowCredentials。开着它 + 通配来源等于让任意站点都能携带凭证读响应，
        // 是典型 CORS 误配，收益为零、风险白送。
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Authorization")
                .allowCredentials(false)
                .maxAge(3600);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/error", "/actuator/**");

        registry.addInterceptor(idempotentInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/error", "/actuator/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 上传的图片通过 /files/upload/{文件名} 直接访问。
        // 目录是「工作目录 + uploads」，也就是说 —— 换一个目录启动，就换了一个图库。
        // 所以这里把绝对路径打印出来并顺手建好目录：
        // 图片 404 时，第一件事就是看日志里这个路径和数据库里记录的 URL 对不对得上。
        String dir = System.getProperty("user.dir") + java.io.File.separator + "uploads";
        try {
            java.nio.file.Files.createDirectories(java.nio.file.Path.of(dir));
        } catch (Exception e) {
            log.warn("创建上传目录失败: {} ({})", dir, e.getMessage());
        }
        log.info("静态资源目录（图片访问根）: {}", dir);
        registry.addResourceHandler("/files/upload/**")
                .addResourceLocations("file:" + dir + "/");
    }
}
