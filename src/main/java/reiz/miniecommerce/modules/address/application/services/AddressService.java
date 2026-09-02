package reiz.miniecommerce.modules.address.application.services;

import reiz.miniecommerce.modules.address.core.entities.Address;
import reiz.miniecommerce.modules.address.core.exceptions.AddressNotFoundException;
import reiz.miniecommerce.modules.address.core.interfaces.repositories.AddressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AddressService {

    private final AddressRepository addressRepository;

    @Transactional(readOnly = true)
    public List<Address> listFor(UUID userId) {
        return addressRepository.findByUserId(userId).stream()
                .sorted(Comparator.comparing(Address::isPrimary).reversed())
                .toList();
    }

    @Transactional
    public Address create(UUID userId, Address address) {
        address.setUserId(userId);
        promoteIfPrimary(userId, address);
        return addressRepository.save(address);
    }

    @Transactional
    public Address update(UUID userId, UUID addressId, Address changes) {
        Address existing = ownedBy(userId, addressId);

        changes.setId(existing.getId());
        changes.setUserId(userId);
        promoteIfPrimary(userId, changes);
        return addressRepository.save(changes);
    }

    @Transactional
    public void delete(UUID userId, UUID addressId) {
        addressRepository.deleteById(ownedBy(userId, addressId).getId());
    }

    /**
     * Marking an address as primary demotes whichever one held the flag.
     *
     * <p>{@code uq_addresses_primary} only allows one per user, so without this the insert
     * would fail. Doing it here means the caller just says "use this one" and it works, which
     * is what every checkout expects.
     */
    private void promoteIfPrimary(UUID userId, Address address) {
        if (address.isPrimary()) {
            addressRepository.clearPrimaryFor(userId);
        }
    }

    private Address ownedBy(UUID userId, UUID addressId) {
        return addressRepository.findById(addressId)
                .filter(address -> userId.equals(address.getUserId()))
                .orElseThrow(() -> new AddressNotFoundException(addressId));
    }
}
