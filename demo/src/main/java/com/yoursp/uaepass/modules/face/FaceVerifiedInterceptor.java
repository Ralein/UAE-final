package com.yoursp.uaepass.modules.face;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoursp.uaepass.model.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;

/**
 * Intercepts requests to methods annotated with {@link FaceVerified}.
 * <p>
 * If the current user does not have a recent verified face verification,
 * returns 403 with instructions to initiate face verification.
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FaceVerifiedInterceptor implements HandlerInterceptor {

    private final FaceVerificationService faceVerificationService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // Only check methods annotated with @FaceVerified
        FaceVerified annotation = handlerMethod.getMethodAnnotation(FaceVerified.class);
        if (annotation == null) {
            return true;
        }

        User user = (User) request.getAttribute("currentUser");
        if (user == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        boolean hasRecent = faceVerificationService.hasRecentVerification(user.getId());

        if (!hasRecent) {
            log.info("Face verification required for userId={}, path={}",
                    user.getId(), request.getRequestURI());

            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                    "error", "FACE_VERIFICATION_REQUIRED",
                    "message", "A recent face verification is required for this operation",
                    "verifyUrl", "/face/verify/initiate")));
            return false;
        }

        return true;
    }
}
