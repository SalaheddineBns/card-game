package com.cardgame.config;

import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Session-cookie authentication with CSRF protection for the Angular SPA (same origin via the dev proxy).
 *
 * <ul>
 *   <li>Session: {@code JSESSIONID}, HttpOnly, SameSite=Lax, Secure outside the local profile.</li>
 *   <li>CSRF: cookie {@code XSRF-TOKEN} (readable by JS), echoed in header {@code X-XSRF-TOKEN}.</li>
 *   <li>Errors: 401/403 are written as ProblemDetail by {@code GlobalExceptionHandler}.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    public static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";
    public static final String CSRF_HEADER_NAME = "X-XSRF-TOKEN";
    public static final String SESSION_COOKIE_NAME = "JSESSIONID";

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http,
                                    CsrfTokenRepository csrfTokenRepository,
                                    SecurityContextRepository securityContextRepository,
                                    @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        return http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .securityContext(context -> context.securityContextRepository(securityContextRepository))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login", "/api/auth/logout")
                        .permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((request, response, ex) ->
                                resolver.resolveException(request, response, null, ex))
                        .accessDeniedHandler((request, response, ex) ->
                                resolver.resolveException(request, response, null, ex)))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                // logout is a documented controller endpoint (AuthController#logout)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .build();
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieName(CSRF_COOKIE_NAME);
        repository.setHeaderName(CSRF_HEADER_NAME);
        repository.setCookiePath("/");
        repository.setCookieCustomizer(cookie -> cookie.sameSite("Lax"));
        return repository;
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /** Applied by the JSON login: new session id (fixation protection) and a fresh CSRF token. */
    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository csrfTokenRepository) {
        CsrfAuthenticationStrategy csrfRotation = new CsrfAuthenticationStrategy(csrfTokenRepository);
        // the SPA handler loads the new token eagerly, so the rotated XSRF-TOKEN cookie is written on the login response
        csrfRotation.setRequestHandler(new SpaCsrfTokenRequestHandler());
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(), csrfRotation));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        // bcrypt, with the {id} prefix so the algorithm can evolve
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}
