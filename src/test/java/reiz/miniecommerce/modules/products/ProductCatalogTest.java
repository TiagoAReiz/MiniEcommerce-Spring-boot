package reiz.miniecommerce.modules.products;

import reiz.miniecommerce.modules.auth.core.entities.AuthenticatedPrincipal;
import reiz.miniecommerce.modules.auth.core.interfaces.repositories.AccessTokenIssuer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ProductCatalogTest {

    /** Smallest valid PNG: an 8-bit RGBA 1x1 pixel. */
    private static final byte[] PNG = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
            0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4,
            (byte) 0x89, 0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41,
            0x54, 0x78, (byte) 0x9C, 0x63, 0x00, 0x01, 0x00, 0x00,
            0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, (byte) 0xB4, 0x00,
            0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, (byte) 0xAE,
            0x42, 0x60, (byte) 0x82
    };

    @Autowired private MockMvc mockMvc;
    @Autowired private AccessTokenIssuer tokenIssuer;

    private String ownerBearer;
    private String userBearer;

    @BeforeEach
    void tokens() {
        ownerBearer = bearerFor(AuthenticatedPrincipal.Role.OWNER);
        userBearer = bearerFor(AuthenticatedPrincipal.Role.USER);
    }

    @Test
    void ownerCreatesAProductAndAnyoneCanReadIt() throws Exception {
        UUID id = createProduct("Caneca de cerâmica", "49.90", 10);

        mockMvc.perform(get("/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Caneca de cerâmica"))
                .andExpect(jsonPath("$.price").value(49.90))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.sellable").value(true));
    }

    @Test
    void catalogueIsPagedAndPublicAndCarriesPhotos() throws Exception {
        String name = "Caneca paginada " + UUID.randomUUID();
        UUID id = createProduct(name, "10.00", 1);
        upload(id, true);

        mockMvc.perform(get("/products").param("q", name).param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].photos.length()").value(1))
                .andExpect(jsonPath("$.content[0].photos[0].isCover").value(true));
    }

    @Test
    void pageSizeIsCappedAtOneHundred() throws Exception {
        mockMvc.perform(get("/products").param("size", "5000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void customersCannotCreateProducts() throws Exception {
        mockMvc.perform(post("/products")
                        .header("Authorization", userBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Proibida", "1.00", 1)))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsANegativePrice() throws Exception {
        mockMvc.perform(post("/products")
                        .header("Authorization", ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Negativa", "-1.00", 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("price"));
    }

    @Test
    void uploadStoresTheImageAndReturnsAReachableUrl() throws Exception {
        UUID productId = createProduct("Com foto " + UUID.randomUUID(), "20.00", 5);

        MvcResult uploaded = mockMvc.perform(multipart("/products/" + productId + "/photos")
                        .file(new MockMultipartFile("file", "foto.png", "image/png", PNG))
                        .param("isCover", "true")
                        .header("Authorization", ownerBearer))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isCover").value(true))
                .andReturn();

        String url = json(uploaded, "url");
        assertThat(url).startsWith("http://localhost:9000/product-photos/");
        // the generated key replaces the uploaded name, keeping only its extension
        assertThat(url).doesNotContain("foto.png").endsWith(".png");

        mockMvc.perform(get("/products/" + productId))
                .andExpect(jsonPath("$.photos.length()").value(1))
                .andExpect(jsonPath("$.photos[0].url").value(url))
                .andExpect(jsonPath("$.photos[0].isCover").value(true));
    }

    @Test
    void aSecondCoverDemotesTheFirst() throws Exception {
        UUID productId = createProduct("Duas fotos " + UUID.randomUUID(), "20.00", 5);

        String first = json(upload(productId, true), "url");
        String second = json(upload(productId, true), "url");

        assertThat(first).isNotEqualTo(second);

        // both photos come back; only the newest carries the cover flag
        MvcResult detail = mockMvc.perform(get("/products/" + productId))
                .andExpect(jsonPath("$.photos.length()").value(2))
                .andReturn();

        List<String> covers = com.jayway.jsonpath.JsonPath.read(
                detail.getResponse().getContentAsString(), "$.photos[?(@.isCover == true)].url");
        assertThat(covers).containsExactly(second);
    }

    @Test
    void refusesAFileThatIsNotAnAllowedImage() throws Exception {
        UUID productId = createProduct("Sem foto " + UUID.randomUUID(), "20.00", 5);

        mockMvc.perform(multipart("/products/" + productId + "/photos")
                        .file(new MockMultipartFile("file", "malware.png", "application/x-msdownload",
                                "MZ".getBytes()))
                        .header("Authorization", ownerBearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PHOTO"));
    }

    @Test
    void deleteRetiresTheProductInsteadOfRemovingIt() throws Exception {
        UUID id = createProduct("Aposentada " + UUID.randomUUID(), "20.00", 5);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/products/" + id).header("Authorization", ownerBearer))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.sellable").value(false));
    }

    @Test
    void ownerUpdatesNamePriceAndStock() throws Exception {
        UUID id = createProduct("Antes " + UUID.randomUUID(), "10.00", 3);
        String newName = "Depois " + UUID.randomUUID();

        mockMvc.perform(put("/products/" + id)
                        .header("Authorization", ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(newName, "25.50", 8)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(newName))
                .andExpect(jsonPath("$.price").value(25.50))
                .andExpect(jsonPath("$.stock").value(8));

        // read back through the cached route: the write has to have evicted the old entry
        mockMvc.perform(get("/products/" + id))
                .andExpect(jsonPath("$.name").value(newName))
                .andExpect(jsonPath("$.price").value(25.50))
                .andExpect(jsonPath("$.stock").value(8));
    }

    @Test
    void updateBringsBackAProductThatWasRetired() throws Exception {
        String name = "Volta " + UUID.randomUUID();
        UUID id = createProduct(name, "20.00", 5);

        mockMvc.perform(delete("/products/" + id).header("Authorization", ownerBearer))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/products/" + id))
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(put("/products/" + id)
                        .header("Authorization", ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(name, "20.00", 5, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.sellable").value(true));
    }

    @Test
    void customersCannotUpdateProducts() throws Exception {
        UUID id = createProduct("Alheia " + UUID.randomUUID(), "10.00", 1);

        mockMvc.perform(put("/products/" + id)
                        .header("Authorization", userBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Sequestrada " + UUID.randomUUID(), "1.00", 1)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updatingAProductThatDoesNotExistIsNotFound() throws Exception {
        mockMvc.perform(put("/products/" + UUID.randomUUID())
                        .header("Authorization", ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson("Fantasma " + UUID.randomUUID(), "1.00", 1)))
                .andExpect(status().isNotFound());
    }

    @Test
    void aPhotoCanBeRepositioned() throws Exception {
        UUID productId = createProduct("Reordenar " + UUID.randomUUID(), "20.00", 5);
        String photoId = json(upload(productId, false), "id");

        mockMvc.perform(patch("/products/" + productId + "/photos/" + photoId)
                        .header("Authorization", ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"position\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value(3))
                .andExpect(jsonPath("$.isCover").value(false));
    }

    @Test
    void promotingAPhotoToCoverDemotesThePrevious() throws Exception {
        UUID productId = createProduct("Troca de capa " + UUID.randomUUID(), "20.00", 5);
        upload(productId, true);
        String secondId = json(upload(productId, false), "id");

        mockMvc.perform(patch("/products/" + productId + "/photos/" + secondId)
                        .header("Authorization", ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isCover\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isCover").value(true));

        // uq_product_photos_cover allows a single cover per product, so promoting one has to
        // have demoted the other in the same transaction
        assertThat(coverIdsOf(productId)).containsExactly(secondId);
    }

    @Test
    void unsettingTheCoverLeavesTheProductWithoutOne() throws Exception {
        UUID productId = createProduct("Sem capa " + UUID.randomUUID(), "20.00", 5);
        String photoId = json(upload(productId, true), "id");

        mockMvc.perform(patch("/products/" + productId + "/photos/" + photoId)
                        .header("Authorization", ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"isCover\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isCover").value(false));

        assertThat(coverIdsOf(productId)).isEmpty();
    }

    @Test
    void aPhotoFromAnotherProductIsRejected() throws Exception {
        UUID owningProduct = createProduct("Dona da foto " + UUID.randomUUID(), "20.00", 5);
        UUID otherProduct = createProduct("Outra " + UUID.randomUUID(), "20.00", 5);
        String photoId = json(upload(owningProduct, true), "id");

        mockMvc.perform(patch("/products/" + otherProduct + "/photos/" + photoId)
                        .header("Authorization", ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"position\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PHOTO"));
    }

    // ---------- helpers ----------

    private String bearerFor(AuthenticatedPrincipal.Role role) {
        return "Bearer " + tokenIssuer.issue(AuthenticatedPrincipal.builder()
                .id(UUID.randomUUID())
                .email(role.name().toLowerCase() + "@exemplo.com")
                .role(role)
                .build()).getToken();
    }

    private UUID createProduct(String name, String price, int stock) throws Exception {
        MvcResult result = mockMvc.perform(post("/products")
                        .header("Authorization", ownerBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(productJson(name, price, stock)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(json(result, "id"));
    }

    private MvcResult upload(UUID productId, boolean cover) throws Exception {
        return mockMvc.perform(multipart("/products/" + productId + "/photos")
                        .file(new MockMultipartFile("file", "foto.png", "image/png", PNG))
                        .param("isCover", String.valueOf(cover))
                        .header("Authorization", ownerBearer))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private String productJson(String name, String price, int stock) {
        return """
                { "name": "%s", "description": "teste", "price": %s, "stock": %d }
                """.formatted(name, price, stock);
    }

    private String productJson(String name, String price, int stock, boolean active) {
        return """
                { "name": "%s", "description": "teste", "price": %s, "stock": %d, "active": %s }
                """.formatted(name, price, stock, active);
    }

    /** Ids of the photos currently flagged as cover — at most one, per the unique index. */
    private List<String> coverIdsOf(UUID productId) throws Exception {
        MvcResult detail = mockMvc.perform(get("/products/" + productId))
                .andExpect(status().isOk())
                .andReturn();

        return com.jayway.jsonpath.JsonPath.read(
                detail.getResponse().getContentAsString(), "$.photos[?(@.isCover == true)].id");
    }

    private String json(MvcResult result, String field) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$." + field);
    }
}
