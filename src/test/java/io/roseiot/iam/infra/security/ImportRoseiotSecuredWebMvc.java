package io.roseiot.iam.infra.security;

import io.roseiot.shared.api.ApiJson;
import io.roseiot.shared.api.ApiExceptionHandler;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Filter chain and JSON security errors for {@code @WebMvcTest} slices. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import({
    AuthenticationWebMvcTestConfiguration.class,
    SecurityConfig.class,
    SecurityExceptionHandler.class,
    ApiJson.class,
    ApiExceptionHandler.class
})
public @interface ImportRoseiotSecuredWebMvc {}
