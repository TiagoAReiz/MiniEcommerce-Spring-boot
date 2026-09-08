package reiz.miniecommerce.modules.products;

import reiz.miniecommerce.modules.auth.core.entities.AuthenticatedPrincipal;
import reiz.miniecommerce.modules.auth.core.interfaces.repositories.AccessTokenIssuer;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The catalogue data a customer actually shops on: the category they filter by, the headline
 * figures on the card, and the spec sheet the comparison table is built from (V6).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductCatalogDataTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AccessTokenIssuer tokenIssuer;

    private String ownerBearer;

    @BeforeEach
    void signIn() {
        ownerBearer = "Bearer " + tokenIssuer.issue(AuthenticatedPrincipal.builder()
                .id(UUID.randomUUID())
                .email(UUID.randomUUID() + "@exemplo.com")
                .role(AuthenticatedPrincipal.Role.OWNER)
                .build()).getToken();
    }

    @Test
    void categoryHighlightsAndSpecsSurviveTheRoundTrip() throws Exception {
        UUID id = create("""
                {"name":"Monitor %s","price":2449.00,"stock":12,"category":"Monitores",
                 "highlights":[{"value":"144","unit":"Hz"},{"value":"27\\"","unit":"QHD"}],
                 "specs":[{"label":"Tela","value":"27\\" IPS · 2560×1440"},
                          {"label":"Taxa de atualização","value":"144 Hz"}]}
                """.formatted(UUID.randomUUID()));

        mockMvc.perform(get("/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("Monitores"))
                .andExpect(jsonPath("$.highlights[0].value").value("144"))
                .andExpect(jsonPath("$.highlights[0].unit").value("Hz"))
                // order is the operator's, and the comparison table lines products up by it
                .andExpect(jsonPath("$.specs[0].label").value("Tela"))
                .andExpect(jsonPath("$.specs[1].label").value("Taxa de atualização"))
                .andExpect(jsonPath("$.specs[1].value").value("144 Hz"));
    }

    /**
     * The regression this whole test class exists for. {@code ProductJpaMapper#toEntity}
     * builds a detached entity that Hibernate merges over the entire row, so a spec sheet
     * left out of that builder is erased rather than left alone — and the way you would find
     * out is a customer opening a product whose specs vanished because someone fixed a typo
     * in the price.
     */
    @Test
    void editingThePriceDoesNotWipeTheSpecSheet() throws Exception {
        String name = "Notebook " + UUID.randomUUID();
        UUID id = create("""
                {"name":"%s","price":5299.00,"stock":7,"category":"Computadores",
                 "highlights":[{"value":"16 GB","unit":"RAM"}],
                 "specs":[{"label":"Memória","value":"16 GB LPDDR5"},{"label":"Peso","value":"1,29 kg"}]}
                """.formatted(name));

        mockMvc.perform(put("/products/" + id)
                        .header("Authorization", ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","price":4999.00,"stock":7,"category":"Computadores",
                                 "highlights":[{"value":"16 GB","unit":"RAM"}],
                                 "specs":[{"label":"Memória","value":"16 GB LPDDR5"},{"label":"Peso","value":"1,29 kg"}]}
                                """.formatted(name)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/products/" + id))
                .andExpect(jsonPath("$.price").value(4999.00))
                .andExpect(jsonPath("$.specs.length()").value(2))
                .andExpect(jsonPath("$.specs[1].value").value("1,29 kg"))
                .andExpect(jsonPath("$.highlights[0].value").value("16 GB"));
    }

    /**
     * An omitted sheet is an empty one, not "keep what is there" — otherwise a row typed by
     * mistake could never be deleted, only overwritten.
     */
    @Test
    void anEditThatOmitsTheSheetClearsIt() throws Exception {
        String name = "SSD " + UUID.randomUUID();
        UUID id = create("""
                {"name":"%s","price":1189.00,"stock":31,
                 "specs":[{"label":"Capacidade","value":"2 TB"}]}
                """.formatted(name));

        mockMvc.perform(put("/products/" + id)
                        .header("Authorization", ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","price":1189.00,"stock":31}
                                """.formatted(name)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/products/" + id))
                .andExpect(jsonPath("$.specs.length()").value(0))
                .andExpect(jsonPath("$.highlights.length()").value(0))
                .andExpect(jsonPath("$.category").doesNotExist());
    }

    /** A product created without any of it reads back as empty lists, never null. */
    @Test
    void aProductWithoutCatalogueDataReadsBackAsEmptyLists() throws Exception {
        UUID id = create("""
                {"name":"Carregador %s","price":229.00,"stock":44}
                """.formatted(UUID.randomUUID()));

        mockMvc.perform(get("/products/" + id))
                .andExpect(jsonPath("$.highlights").isArray())
                .andExpect(jsonPath("$.highlights.length()").value(0))
                .andExpect(jsonPath("$.specs").isArray())
                .andExpect(jsonPath("$.specs.length()").value(0));
    }

    /**
     * The card and the product header are laid out for three figures. A fourth would not be
     * rendered, and dropping it in silence is worse than refusing the request.
     */
    @Test
    void aFourthHighlightIsRefused() throws Exception {
        mockMvc.perform(post("/products")
                        .header("Authorization", ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"TV %s","price":6890.00,"stock":4,
                                 "highlights":[{"value":"55\\"","unit":"OLED"},{"value":"120","unit":"Hz"},
                                               {"value":"1300","unit":"nits"},{"value":"4","unit":"HDMI"}]}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void aHighlightWithoutAValueIsRefused() throws Exception {
        mockMvc.perform(post("/products")
                        .header("Authorization", ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Fone %s","price":899.00,"stock":3,
                                 "highlights":[{"value":"","unit":"Hz"}]}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isBadRequest());
    }

    private UUID create(String json) throws Exception {
        String body = mockMvc.perform(post("/products")
                        .header("Authorization", ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return UUID.fromString(JsonPath.read(body, "$.id"));
    }
}
