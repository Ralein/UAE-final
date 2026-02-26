package com.yoursp.uaepass.modules.face;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yoursp.uaepass.model.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.method.HandlerMethod;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FaceVerifiedInterceptorTest {

    @Mock
    private FaceVerificationService faceVerificationService;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private FaceVerifiedInterceptor interceptor;

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private HandlerMethod handlerMethod;

    @Test
    @DisplayName("Non-annotated method → always allowed")
    void nonAnnotatedMethodAllowed() throws Exception {
        when(handlerMethod.getMethodAnnotation(FaceVerified.class)).thenReturn(null);

        boolean result = interceptor.preHandle(request, response, handlerMethod);

        assertTrue(result);
    }

    @Test
    @DisplayName("Annotated + recent verification → allowed")
    void annotatedWithRecentVerification() throws Exception {
        FaceVerified annotation = mock(FaceVerified.class);
        when(handlerMethod.getMethodAnnotation(FaceVerified.class)).thenReturn(annotation);

        User user = User.builder().id(UUID.randomUUID()).build();
        when(request.getAttribute("currentUser")).thenReturn(user);
        when(faceVerificationService.hasRecentVerification(user.getId())).thenReturn(true);

        boolean result = interceptor.preHandle(request, response, handlerMethod);

        assertTrue(result);
    }

    @Test
    @DisplayName("Annotated + no verification → 403 with FACE_VERIFICATION_REQUIRED")
    void annotatedWithoutVerification() throws Exception {
        FaceVerified annotation = mock(FaceVerified.class);
        when(handlerMethod.getMethodAnnotation(FaceVerified.class)).thenReturn(annotation);

        User user = User.builder().id(UUID.randomUUID()).build();
        when(request.getAttribute("currentUser")).thenReturn(user);
        when(request.getRequestURI()).thenReturn("/signature/initiate");
        when(faceVerificationService.hasRecentVerification(user.getId())).thenReturn(false);

        StringWriter sw = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        boolean result = interceptor.preHandle(request, response, handlerMethod);

        assertFalse(result);
        verify(response).setStatus(403);
        assertTrue(sw.toString().contains("FACE_VERIFICATION_REQUIRED"));
    }

    @Test
    @DisplayName("Annotated + no user → 401")
    void annotatedWithNoUser() throws Exception {
        FaceVerified annotation = mock(FaceVerified.class);
        when(handlerMethod.getMethodAnnotation(FaceVerified.class)).thenReturn(annotation);
        when(request.getAttribute("currentUser")).thenReturn(null);

        boolean result = interceptor.preHandle(request, response, handlerMethod);

        assertFalse(result);
        verify(response).setStatus(401);
    }
}
