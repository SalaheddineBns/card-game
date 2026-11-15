package com.cardgame;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.Optional;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Behaves like the Angular app: keeps cookies between requests and copies the {@code XSRF-TOKEN} cookie
 * into the {@code X-XSRF-TOKEN} header on state-changing requests. Never throws on 4xx/5xx.
 */
public final class SpaHttpClient {

    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    private final URI baseUri;
    private final RestClient rest;

    public SpaHttpClient(int port) {
        this.baseUri = URI.create("http://localhost:" + port);
        HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).build();
        this.rest = RestClient.builder()
                .baseUrl(baseUri.toString())
                .requestFactory(new JdkClientHttpRequestFactory(http))
                .defaultStatusHandler(status -> true, (request, response) -> { })
                .build();
    }

    public ResponseEntity<String> get(String path) {
        return rest.get().uri(path).retrieve().toEntity(String.class);
    }

    /** POST with the CSRF header taken from the cookie, as Angular's HttpClient does. */
    public ResponseEntity<String> post(String path, String json) {
        return send(path, json, true);
    }

    public ResponseEntity<String> postWithoutCsrfHeader(String path, String json) {
        return send(path, json, false);
    }

    public String baseUrl() {
        return baseUri.toString();
    }

    public Optional<String> cookie(String name) {
        return cookies.getCookieStore().get(baseUri).stream()
                .filter(c -> c.getName().equals(name))
                .map(HttpCookie::getValue)
                .findFirst();
    }

    private ResponseEntity<String> send(String path, String json, boolean withCsrf) {
        RestClient.RequestBodySpec spec = rest.method(HttpMethod.POST).uri(path);
        if (withCsrf) {
            cookie("XSRF-TOKEN").ifPresent(token -> spec.header("X-XSRF-TOKEN", token));
        }
        if (json != null) {
            spec.contentType(MediaType.APPLICATION_JSON).body(json);
        }
        return spec.retrieve().toEntity(String.class);
    }
}
