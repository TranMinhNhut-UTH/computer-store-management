package com.computerstore.computer_store_backend.shared.pagination;

import com.computerstore.computer_store_backend.shared.error.BusinessException;
import java.util.function.Supplier;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/** Pagination for the existing employee, catalog and partner list contracts. */
public final class ListPagination {
    private ListPagination() {}

    public static PageRequest request(int page, int size, String sortProperty,
            Supplier<BusinessException> invalidRequest) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw invalidRequest.get();
        }
        return PageRequest.of(page, size, Sort.by(sortProperty, "id"));
    }
}
