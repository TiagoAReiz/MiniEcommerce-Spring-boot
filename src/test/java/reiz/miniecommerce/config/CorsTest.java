package reiz.miniecommerce.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Without this the front end cannot reach the API at all: the browser refuses the response
 * before any application code sees the request.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CorsTest {

    private static final String FRONT_END = "http://localhost:3000";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void preflightFromTheFrontEndIsAllowed() throws Exception {
        mockMvc.perform(options("/products")
                        .header("Origin", FRONT_END)
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", FRONT_END));
    }

    @Test
    void preflightCarriesTheAuthorizationHeaderThroughForProtectedRoutes() throws Exception {
        mockMvc.perform(options("/orders")
                        .header("Origin", FRONT_END)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Authorization, Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().stringValues("Access-Control-Allow-Headers",
                        "Authorization, Content-Type"));
    }

    @Test
    void anUnknownOriginIsRefused() throws Exception {
        // the whole point of listing origins: a page the operator never approved must not be
        // able to drive the API, even holding a token it obtained some other way
        mockMvc.perform(options("/products")
                        .header("Origin", "https://site-que-ninguem-autorizou.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    @Test
    void theLocationHeaderIsReadableByTheBrowser() throws Exception {
        // POST responses answer 201 with Location; a browser hides it unless it is exposed,
        // and the front end would have no way to learn the id of what it just created
        mockMvc.perform(get("/products")
                        .header("Origin", FRONT_END))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Expose-Headers", "Location"));
    }

    @Test
    void aPreflightNeedsNoToken() throws Exception {
        // the browser sends OPTIONS without Authorization; answering 401 here would break
        // every protected route before the real request is ever attempted
        mockMvc.perform(options("/users/me")
                        .header("Origin", FRONT_END)
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk());
    }
}
