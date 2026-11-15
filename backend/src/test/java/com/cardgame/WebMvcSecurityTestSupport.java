package com.cardgame;

import com.cardgame.config.SecurityConfig;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** For {@code @WebMvcTest}: the real security chain, with a mocked user store. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(SecurityConfig.class)
@MockitoBean(types = UserDetailsService.class)
public @interface WebMvcSecurityTestSupport {
}
