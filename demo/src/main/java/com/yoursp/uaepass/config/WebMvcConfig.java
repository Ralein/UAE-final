package com.yoursp.uaepass.config;

import com.yoursp.uaepass.modules.face.FaceVerifiedInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the {@link FaceVerifiedInterceptor} to handle {@code @FaceVerified}
 * annotations.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final FaceVerifiedInterceptor faceVerifiedInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(faceVerifiedInterceptor)
                .addPathPatterns("/**");
    }
}
