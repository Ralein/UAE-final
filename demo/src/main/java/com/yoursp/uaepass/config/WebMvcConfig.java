package com.yoursp.uaepass.config;

import com.yoursp.uaepass.modules.face.FaceVerifiedInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the {@link FaceVerifiedInterceptor} to handle {@code @FaceVerified}
 * annotations.
 */
@SuppressWarnings("null")
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final FaceVerifiedInterceptor faceVerifiedInterceptor;

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(faceVerifiedInterceptor)
                .addPathPatterns("/**");
    }
}
