package reiz.miniecommerce.modules.auth;

import reiz.miniecommerce.modules.auth.core.entities.AuthenticatedPrincipal;
import reiz.miniecommerce.modules.auth.core.entities.IssuedToken;
import reiz.miniecommerce.modules.auth.core.interfaces.repositories.AccessTokenIssuer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccessTokenIssuer accessTokenIssuer;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Test
    void issuedTokenCarriesIdentityAndRoleAndIsAcceptedBack() {
        UUID id = UUID.randomUUID();
        IssuedToken issued = accessTokenIssuer.issue(AuthenticatedPrincipal.builder()
                .id(id)
                .email("dono@loja.com")
                .role(AuthenticatedPrincipal.Role.OWNER)
                .build());

        assertThat(issued.getExpiresInSeconds()).isEqualTo(3600);

        Jwt decoded = jwtDecoder.decode(issued.getToken());
        assertThat(decoded.getSubject()).isEqualTo(id.toString());
        assertThat(decoded.getClaimAsString("role")).isEqualTo("OWNER");
        assertThat(decoded.getClaimAsString("email")).isEqualTo("dono@loja.com");
    }

    @Test
    void protectedEndpointRejectsRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointRejectsAForgedToken() throws Exception {
        mockMvc.perform(get("/orders").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointAcceptsATokenThisApiIssued() throws Exception {
        IssuedToken issued = accessTokenIssuer.issue(AuthenticatedPrincipal.builder()
                .id(UUID.randomUUID())
                .email("cliente@teste.com")
                .role(AuthenticatedPrincipal.Role.USER)
                .build());

        // authentication passes, so the request reaches the handler instead of being
        // turned away with 401
        mockMvc.perform(get("/orders").header("Authorization", "Bearer " + issued.getToken()))
                .andExpect(status().isOk());
    }

    @Test
    void signInRejectsSomethingThatIsNotAGoogleToken() throws Exception {
        mockMvc.perform(post("/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"clearly.not.google\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signInRejectsAnEmptyBody() throws Exception {
        mockMvc.perform(post("/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
