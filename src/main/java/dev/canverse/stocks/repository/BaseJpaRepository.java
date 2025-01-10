package dev.canverse.stocks.repository;

import dev.canverse.stocks.domain.exception.NotFoundException;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.lang.NonNull;

@NoRepositoryBean
public interface BaseJpaRepository<T, ID> extends JpaRepository<T, ID>, JpaSpecificationExecutor<T> {
    default String getNotFoundMessage() {
        return "Kayıt bulunamadı.";
    }

    default T getReference(@NonNull ID id) {
        if (!existsById(id))
            throw new NotFoundException(getNotFoundMessage(), id);

        return getReferenceById(id);
    }

    default T getReference(@NonNull ID id, String notFoundMessage) {
        if (!existsById(id))
            throw new NotFoundException(notFoundMessage);

        return getReferenceById(id);
    }

    default T getById(@NonNull ID id) {
        return findById(id).orElseThrow(() -> new NotFoundException(getNotFoundMessage(), id));
    }

    default T getById(ID id, String notFoundMessage) {
        return findById(id).orElseThrow(() -> new NotFoundException(notFoundMessage, id));
    }
}