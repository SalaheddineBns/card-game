package com.cardgame.config;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the Angular build (copied into {@code classpath:/static} by the Dockerfile) from the same origin as the
 * API, so the session cookie, CSRF and the WebSocket all stay same-origin.
 *
 * <ul>
 *   <li>Existing files are served as is. Angular's hashed bundles ({@code *.js}, {@code *.css}, {@code media/})
 *       are cached for a year; everything else (index.html, favicon) must be revalidated.</li>
 *   <li>Any other GET whose last segment has no dot (a client-side route like {@code /lobby} or
 *       {@code /games/12}) gets index.html, except under the server prefixes below, which keep their 401/404.</li>
 * </ul>
 * Controllers are matched before this handler, so it never shadows an endpoint.
 */
@Configuration(proxyBeanMethods = false)
public class SpaWebConfig implements WebMvcConfigurer {

    static final String STATIC_LOCATION = "classpath:/static/";
    private static final List<String> SERVER_PREFIXES =
            List.of("api/", "api", "ws", "ws/", "actuator", "actuator/", "v3/api-docs", "swagger-ui", "error");

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/*.js", "/*.css", "/*.mjs", "/media/**")
                .addResourceLocations(STATIC_LOCATION)
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
        registry.addResourceHandler("/**")
                .addResourceLocations(STATIC_LOCATION)
                .setCacheControl(CacheControl.noCache())
                .resourceChain(true)
                .addResolver(new SpaFallbackResolver());
    }

    /** Serves the file if it exists, otherwise index.html for client-side routes. */
    static final class SpaFallbackResolver extends PathResourceResolver {

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource requested = location.createRelative(resourcePath);
            if (requested.exists() && requested.isReadable()) {
                return requested;
            }
            if (!isClientRoute(resourcePath)) {
                return null;
            }
            Resource index = new ClassPathResource("static/index.html");
            return index.exists() ? index : null;
        }

        static boolean isClientRoute(String path) {
            String lastSegment = path.substring(path.lastIndexOf('/') + 1);
            boolean serverPath = SERVER_PREFIXES.stream().anyMatch(prefix -> path.equals(prefix)
                    || path.startsWith(prefix.endsWith("/") ? prefix : prefix + "/"));
            return !serverPath && !lastSegment.contains(".");
        }
    }
}
