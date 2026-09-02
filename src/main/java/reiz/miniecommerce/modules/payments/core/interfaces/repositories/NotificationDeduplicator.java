package reiz.miniecommerce.modules.payments.core.interfaces.repositories;

/**
 * Guards against processing the same gateway notification twice.
 *
 * <p>Payment providers retry until they get a 2xx, and a retry after a slow-but-successful
 * run would otherwise settle the same order again.
 */
public interface NotificationDeduplicator {

    /**
     * @return true the first time this id is seen, false on every repeat
     */
    boolean claim(String notificationId);

    /**
     * Gives a claim back after processing failed.
     *
     * <p>Without this a failed run would swallow every retry as a duplicate, and a payment
     * the customer already made would never settle.
     */
    void release(String notificationId);
}
