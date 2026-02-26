package com.yoursp.uaepass.modules.face;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks controller methods that require a recent face verification.
 * <p>
 * The {@link FaceVerifiedInterceptor} checks for a verified face verification
 * within the configured time window (default 15 minutes).
 * If no recent verification exists, returns 403 with
 * {@code {"error":"FACE_VERIFICATION_REQUIRED","verifyUrl":"/face/verify/initiate"}}.
 * </p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface FaceVerified {
}
