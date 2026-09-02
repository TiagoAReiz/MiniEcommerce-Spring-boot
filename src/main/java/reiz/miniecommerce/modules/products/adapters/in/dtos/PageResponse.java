package reiz.miniecommerce.modules.products.adapters.in.dtos;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * The paginated envelope the API contract promises. Spring's own Page serializes with
 * fields that are not part of our contract, so it is mapped explicitly.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
