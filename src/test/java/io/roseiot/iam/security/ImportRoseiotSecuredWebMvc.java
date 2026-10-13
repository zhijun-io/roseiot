package io.roseiot.iam.security;

import io.roseiot.config.ApiExceptionHandler;
import io.roseiot.config.JacksonConfig;
import io.roseiot.config.WebSecurityConfig;
import io.roseiot.shared.security.SecurityExceptionHandler;
import io.roseiot.shared.webmvc.ApiHttpSupport;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;

/** Filter chain and JSON security errors for {@code @WebMvcTest} slices. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import({
    AuthenticationWebMvcTestConfiguration.class,
    WebSecurityConfig.class,
    JacksonConfig.class,
    SecurityExceptionHandler.class,
    ApiHttpSupport.class,
    ApiExceptionHandler.class
})
public @interface ImportRoseiotSecuredWebMvc {}
