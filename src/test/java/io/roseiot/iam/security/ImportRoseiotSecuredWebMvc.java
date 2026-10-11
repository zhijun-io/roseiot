package io.roseiot.iam.security;

import io.roseiot.shared.webmvc.ApiJson;
import io.roseiot.shared.webmvc.ApiExceptionHandler;
import io.roseiot.iam.security.SecurityExceptionHandler;
import io.roseiot.iam.security.WebSecurityConfig;
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
    WebSecurityConfig.class,
    SecurityExceptionHandler.class,
    ApiJson.class,
    ApiExceptionHandler.class
})
public @interface ImportRoseiotSecuredWebMvc {}
