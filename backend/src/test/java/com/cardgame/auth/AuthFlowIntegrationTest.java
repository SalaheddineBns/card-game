package com.cardgame.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.cardgame.SpaHttpClient;
import com.cardgame.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** Register → login → me → logout over real HTTP with the session cookie and the CSRF cookie/header. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.servlet.session.cookie.secure=false")
@Import(TestcontainersConfiguration.class)
class AuthFlowIntegrationTest {

    @LocalServerPort
    int port;

    @Test
    void registerLoginMeLogout() {
        SpaHttpClient client = new SpaHttpClient(port);

        // the first request gets 401 and the XSRF-TOKEN cookie
        ResponseEntity<String> anonymousMe = client.get("/api/auth/me");
        assertThat(anonymousMe.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(client.cookie("XSRF-TOKEN")).isPresent();

        // a POST without the header is refused
        String registration = """
                {"username":"Flow_User","password":"password123","age":21,"gender":"OTHER"}""";
        assertThat(client.postWithoutCsrfHeader("/api/auth/register", registration).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        // register
        ResponseEntity<String> registered = client.post("/api/auth/register", registration);
        assertThat(registered.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat((String) JsonPath.read(registered.getBody(), "$.username")).isEqualTo("Flow_User");
        assertThat(registered.getBody()).doesNotContain("password");

        // same name with another case is taken (D18)
        ResponseEntity<String> duplicate = client.post("/api/auth/register", """
                {"username":"flow_user","password":"password123","age":21}""");
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getHeaders().getContentType()).hasToString("application/problem+json");

        // registering does not log in
        assertThat(client.get("/api/auth/me").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // wrong password
        ResponseEntity<String> badLogin = client.post("/api/auth/login", """
                {"username":"Flow_User","password":"not-the-password"}""");
        assertThat(badLogin.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat((String) JsonPath.read(badLogin.getBody(), "$.detail")).isEqualTo("Nom d'utilisateur ou mot de passe invalide.");
        assertThat((String) JsonPath.read(badLogin.getBody(), "$.title")).isEqualTo("Échec de la connexion");

        // login typed in upper case (D18, G2) sets the session cookie and rotates the CSRF token
        String tokenBeforeLogin = client.cookie("XSRF-TOKEN").orElseThrow();
        ResponseEntity<String> login = client.post("/api/auth/login", """
                {"username":"FLOW_USER","password":"password123"}""");
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((String) JsonPath.read(login.getBody(), "$.username")).isEqualTo("Flow_User");
        assertThat(login.getHeaders().get(HttpHeaders.SET_COOKIE))
                .anySatisfy(c -> assertThat(c).startsWith("JSESSIONID=").contains("HttpOnly").contains("SameSite=Lax"))
                .anySatisfy(c -> assertThat(c).startsWith("XSRF-TOKEN=").contains("SameSite=Lax").doesNotContain("HttpOnly"));
        assertThat(client.cookie("JSESSIONID")).isPresent();
        assertThat(client.cookie("XSRF-TOKEN")).isPresent().get().isNotEqualTo(tokenBeforeLogin);

        // me
        ResponseEntity<String> me = client.get("/api/auth/me");
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((String) JsonPath.read(me.getBody(), "$.username")).isEqualTo("Flow_User");
        assertThat((Integer) JsonPath.read(me.getBody(), "$.age")).isEqualTo(21);
        assertThat((String) JsonPath.read(me.getBody(), "$.gender")).isEqualTo("OTHER");

        // logout
        // exactly what the generated Angular client sends for a 204 endpoint
        assertThat(client.postAccepting("/api/auth/logout", "application/problem+json").getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);

        // the session is gone
        assertThat(client.get("/api/auth/me").getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void loginRequiresTheCsrfHeader() {
        SpaHttpClient client = new SpaHttpClient(port);
        client.get("/api/auth/me");

        ResponseEntity<String> login = client.postWithoutCsrfHeader("/api/auth/login", """
                {"username":"whoever","password":"password123"}""");

        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
