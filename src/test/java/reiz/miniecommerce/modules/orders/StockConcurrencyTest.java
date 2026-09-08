package reiz.miniecommerce.modules.orders;

import reiz.miniecommerce.modules.address.core.entities.Address;
import reiz.miniecommerce.modules.address.core.interfaces.repositories.AddressRepository;
import reiz.miniecommerce.modules.cart.core.entities.Cart;
import reiz.miniecommerce.modules.cart.core.interfaces.repositories.CartRepository;
import reiz.miniecommerce.modules.orders.application.services.CheckoutService;
import reiz.miniecommerce.modules.orders.core.entities.Order;
import reiz.miniecommerce.modules.owners.core.interfaces.repositories.OwnerRepository;
import reiz.miniecommerce.modules.products.core.entities.Product;
import reiz.miniecommerce.modules.products.core.interfaces.repositories.ProductRepository;
import reiz.miniecommerce.modules.users.core.entities.User;
import reiz.miniecommerce.modules.users.core.interfaces.repositories.UserRepository;
import reiz.miniecommerce.testsupport.Storefront;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Two customers racing for the last unit in stock.
 *
 * <p>{@code CheckoutService} reads the product, validates the quantity and writes the new
 * stock inside one transaction, but nothing stops a second transaction from reading the same
 * row in between. Under Postgres' default READ COMMITTED isolation both can see one unit
 * available and both can commit, which would sell an item that does not exist and can drive
 * the stock below zero.
 *
 * <p>The checkout is driven through {@link CheckoutService} rather than MockMvc on purpose.
 * MockMvc runs the request on the calling thread with no servlet container behind it, so
 * using it here would only add its own threading questions on top of the one being measured.
 * The race lives in the service transaction, and that is what these two threads exercise.
 */
@SpringBootTest
@TestPropertySource(properties = {
        // the race is the only thing racing here: a dead endpoint keeps a CEP lookup off the
        // network, and zero freight keeps it out of the price
        "app.shipping.cep-base-url=http://127.0.0.1:1",
        "app.shipping.fallback-cost=0.00"
})
class StockConcurrencyTest {

    private static final BigDecimal PRICE = new BigDecimal("49.90");

    @Autowired private CheckoutService checkoutService;
    @Autowired private UserRepository userRepository;
    @Autowired private AddressRepository addressRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CartRepository cartRepository;
    @Autowired private OwnerRepository ownerRepository;
    @Autowired private DataSource dataSource;

    private UUID productId;
    private Buyer first;
    private Buyer second;

    /** A customer ready to check out: complete profile, own address, product already in cart. */
    private record Buyer(UUID userId, UUID addressId) {
    }

    /** What one checkout attempt ended up doing. */
    private record Attempt(Order order, Throwable failure) {

        boolean succeeded() {
            return order != null;
        }
    }

    @BeforeEach
    void setUp() {
        Storefront.sellsWithFreight(ownerRepository);

        productId = productRepository.save(Product.builder()
                .name("Ultima unidade " + UUID.randomUUID())
                .price(PRICE)
                .stock(1)
                .active(true)
                .build()).getId();

        first = readyBuyer();
        second = readyBuyer();
    }

    @Test
    void onlyOneOfTwoSimultaneousCheckoutsGetsTheLastUnit() throws Exception {
        List<Attempt> attempts = raceForTheLastUnit();

        List<Attempt> won = attempts.stream().filter(Attempt::succeeded).toList();
        List<Attempt> lost = attempts.stream().filter(attempt -> !attempt.succeeded()).toList();

        int finalStock = stockOf(productId);
        int itemsSold = orderItemsFor(productId);
        String outcome = "stock=%d, order items=%d, succeeded=%d, failed=%s"
                .formatted(finalStock, itemsSold, won.size(), reasons(lost));

        assertThat(finalStock)
                .as("stock must never go below zero — %s", outcome)
                .isGreaterThanOrEqualTo(0);

        assertThat(itemsSold)
                .as("one unit cannot be sold twice — %s", outcome)
                .isLessThanOrEqualTo(1);

        assertThat(won)
                .as("exactly one checkout should have taken the last unit — %s", outcome)
                .hasSize(1);

        assertThat(lost)
                .as("the other checkout should have been turned away — %s", outcome)
                .hasSize(1);
    }

    /**
     * Both threads park on the same gate and are released together, so the two transactions
     * overlap instead of running one after the other.
     */
    private List<Attempt> raceForTheLastUnit() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch bothReady = new CountDownLatch(2);
        CountDownLatch startGate = new CountDownLatch(1);

        try {
            Future<Attempt> one = pool.submit(attempt(first, bothReady, startGate));
            Future<Attempt> two = pool.submit(attempt(second, bothReady, startGate));

            bothReady.await(10, TimeUnit.SECONDS);
            startGate.countDown();

            return List.of(one.get(30, TimeUnit.SECONDS), two.get(30, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }

    /** Names the exceptions the losing attempts hit, so a failure report explains itself. */
    private String reasons(List<Attempt> lost) {
        return lost.isEmpty()
                ? "none"
                : lost.stream()
                        .map(attempt -> attempt.failure().getClass().getSimpleName())
                        .toList()
                        .toString();
    }

    private Callable<Attempt> attempt(Buyer buyer, CountDownLatch ready, CountDownLatch gate) {
        return () -> {
            ready.countDown();
            gate.await();
            try {
                return new Attempt(checkoutService.checkout(buyer.userId(), buyer.addressId()), null);
            } catch (Throwable t) {
                return new Attempt(null, t);
            }
        };
    }

    private Buyer readyBuyer() {
        User user = userRepository.save(User.fromGoogleProfile(
                "sub-" + UUID.randomUUID(), "Comprador", UUID.randomUUID() + "@exemplo.com", null));
        user.setCpf(uniqueCpf());
        user.setPhone("+5511999999999");
        user = userRepository.save(user);

        UUID addressId = addressRepository.save(Address.builder()
                .userId(user.getId()).zipCode("01310100").street("Avenida Paulista")
                .streetNumber("1578").neighborhood("Bela Vista").city("Sao Paulo")
                .state("SP").country("BR").primary(false)
                .build()).getId();

        Cart cart = Cart.empty(user.getId());
        // the cart price has to match the product exactly, or checkout refuses with
        // PRICE_CHANGED before it ever reaches the stock check this test is about
        cart.addLine(productId, 1, PRICE);
        cartRepository.save(cart);

        return new Buyer(user.getId(), addressId);
    }

    /** Eleven digits, unique per buyer: {@code uq_users_cpf} is global across runs. */
    private String uniqueCpf() {
        return String.format("%011d",
                Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 100_000_000_000L));
    }

    /**
     * Read straight from the database rather than through the repository: product reads go
     * through the Redis cache, and this test needs the row as it actually stands.
     */
    private int stockOf(UUID id) throws SQLException {
        return queryInt("SELECT stock FROM products WHERE id = ?", id);
    }

    private int orderItemsFor(UUID id) throws SQLException {
        return queryInt("SELECT count(*) FROM order_items WHERE product_id = ?", id);
    }

    private int queryInt(String sql, UUID id) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setObject(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
}
