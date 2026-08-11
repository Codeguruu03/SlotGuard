package com.slotguard.application.repository;

import com.slotguard.application.model.Slot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SlotRepository extends JpaRepository<Slot, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Slot s WHERE s.id = :id")
    Optional<Slot> findByIdWithPessimisticLock(@Param("id") Long id);

    @Modifying
    @Query("UPDATE Slot s SET s.reservedCount = s.reservedCount + 1 WHERE s.id = :id AND s.reservedCount < s.capacity")
    int incrementReservedCountAtomic(@Param("id") Long id);
}
