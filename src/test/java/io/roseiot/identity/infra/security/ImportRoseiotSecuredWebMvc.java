package io.roseiot.identity.infra.security;

import io.roseiot.shared.api.ApiErrorBodyWriter;
import io.roseiot.shared.api.ApiExceptionHandler;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Filter chain, JSON security errors, and {@code @authz} for {@code @WebMvcTest} slices. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import({
    SecurityConfig.class,
    RoseiotAuthorization.class,
    JsonSecurityHandlers.class,
    ApiErrorBodyWriter.class,
    ApiExceptionHandler.class
})
public @interface ImportRoseiotSecuredWebMvc {}
