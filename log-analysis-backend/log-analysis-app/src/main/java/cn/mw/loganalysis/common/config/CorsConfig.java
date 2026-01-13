package cn.mw.loganalysis.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.Collections;

/**
 * 跨域资源共享(CORS)配置
 */
@Configuration
public class CorsConfig {

    /**
     * 配置CORS过滤器
     */
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        // 允许的源（生产环境应该配置具体的域名）
        config.setAllowedOriginPatterns(Collections.singletonList("*"));

        // 允许的HTTP方法
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

        // 允许的请求头
        config.setAllowedHeaders(Arrays.asList("*"));

        // 允许携带认证信息（cookies）
        config.setAllowCredentials(true);

        // 暴露的响应头
        config.setExposedHeaders(Arrays.asList(
                "Authorization",
                "X-Total-Count",
                "Content-Disposition"
        ));

        // 预检请求的有效期（秒）
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(source);
    }
}
