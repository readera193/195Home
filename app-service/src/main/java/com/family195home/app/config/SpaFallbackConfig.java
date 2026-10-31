package com.family195home.app.config;

import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * 前端使用 BrowserRouter，直接開啟或重新整理 /login 等前端路由時， 找不到對應靜態檔案的請求需回傳 index.html，交由前端路由處理（API 與 actuator
 * 路徑除外）。
 */
@Configuration
public class SpaFallbackConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(
                        new PathResourceResolver() {
                            @Override
                            protected Resource getResource(String resourcePath, Resource location)
                                    throws IOException {
                                Resource requested = location.createRelative(resourcePath);
                                if (requested.exists() && requested.isReadable()) {
                                    return requested;
                                }
                                if (resourcePath.startsWith("api/")
                                        || resourcePath.startsWith("actuator/")) {
                                    return null;
                                }
                                return new ClassPathResource("/static/index.html");
                            }
                        });
    }
}
