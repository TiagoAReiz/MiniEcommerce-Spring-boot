package reiz.miniecommerce.config;

import reiz.miniecommerce.modules.auth.core.entities.AuthenticatedPrincipal;
import reiz.miniecommerce.modules.auth.core.interfaces.repositories.AccessTokenIssuer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The global net has to catch what nobody claimed, and catch nothing else.
 *
 * <p>The second half is the fragile part: Spring resolves an exception by taking the first
 * advice in order that has any matching method, so a catch-all placed ahead of the module
 * advices would quietly turn every curated 404 and 409 into a 500.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GlobalExceptionHandlerTest {

    /**
     * Nothing in the application throws an unhandled exception on demand, so the catch-all
     * needs a route that does. It sits under {@code /auth/}, the one prefix the filter chain
     * lets through unauthenticated, so the request reaches the dispatcher.
     */
    @TestConfiguration
    static class ExplodingRoute {

        // Deliberately not @RestController: the class sits in a package the application
        // scans, so the stereotype would register it once by scanning and once by the @Bean
        // below. A type-level @RequestMapping is enough for it to be treated as a handler.
        @RequestMapping
        static class Boom {
            @GetMapping("/auth/explode-for-test")
            @ResponseBody
            public String explode() {
                throw new IllegalStateException(
                        "duplicate key value violates unique constraint uq_users_email");
            }
        }

        @Bean
        Boom explodingController() {
            return new Boom();
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private AccessTokenIssuer tokenIssuer;

    private String bearer;

    @BeforeEach
    void token() {
        UUID id = UUID.randomUUID();
        bearer = "Bearer " + tokenIssuer.issue(AuthenticatedPrincipal.builder()
                .id(id).email(id + "@exemplo.com")
                .role(AuthenticatedPrincipal.Role.USER).build()).getToken();
    }

    @Test
    void malformedJsonIsFourHundredNotFiveHundred() throws Exception {
        mockMvc.perform(post("/cart/items")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void anIdThatIsNotAUuidIsFourHundred() throws Exception {
        mockMvc.perform(get("/products/nao-e-um-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.parameter").value("id"));
    }

    @Test
    void theWrongMethodOnAnExistingRouteIsFourOhFive() throws Exception {
        mockMvc.perform(delete("/auth/google"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void aBodyThatIsNotJsonIsFourFifteen() throws Exception {
        mockMvc.perform(post("/cart/items")
                        .header("Authorization", bearer)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("nao sou json"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void anUnexpectedFailureIsFiveHundredAndNeverEchoesItsOwnMessage() throws Exception {
        mockMvc.perform(get("/auth/explode-for-test"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("Erro inesperado"))
                // the message names a table and a column; neither may reach the caller
                .andExpect(jsonPath("$.detail")
                        .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("users"))))
                .andExpect(jsonPath("$.incident").isNotEmpty());
    }

    // ---------- the part that matters: the net must not steal from the modules ----------

    @Test
    void aModuleKeepsItsOwnStatusForItsOwnException() throws Exception {
        // ProductNotFoundException is a plain RuntimeException; if the catch-all won, this
        // would be 500 instead of the 404 ProductExceptionHandler produces
        mockMvc.perform(get("/products/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Produto não encontrado"));
    }

    @Test
    void anotherModuleAlsoKeepsItsOwnStatus() throws Exception {
        mockMvc.perform(get("/orders/" + UUID.randomUUID()).header("Authorization", bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Pedido não encontrado"));
    }

    @Test
    void beanValidationStillProducesItsFieldErrors() throws Exception {
        // ValidationExceptionHandler is global too, so it competes with the net directly
        mockMvc.perform(post("/cart/items")
                        .header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + UUID.randomUUID() + "\",\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("quantity"));
    }
}
