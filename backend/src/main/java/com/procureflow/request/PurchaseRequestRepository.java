package com.procureflow.request;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PurchaseRequestRepository
        extends JpaRepository<PurchaseRequest, Long>, JpaSpecificationExecutor<PurchaseRequest> {

    /** Row-level lock for state transitions so two users cannot act on the same request at once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PurchaseRequest r where r.id = :id")
    Optional<PurchaseRequest> findByIdForUpdate(@Param("id") Long id);

    @Query("select r.status, count(r) from PurchaseRequest r where r.requester.id = :userId group by r.status")
    List<Object[]> statusCountsForRequester(@Param("userId") Long userId);

    @Query("select r.status, count(r) from PurchaseRequest r group by r.status")
    List<Object[]> statusCounts();

    long countByStatus(RequestStatus status);

    /** Returns null when there are no matching rows. */
    @Query("select sum(r.totalEstimate) from PurchaseRequest r where r.status = :status")
    BigDecimal sumTotalByStatus(@Param("status") RequestStatus status);

    @Query("select r.status, count(r), sum(r.totalEstimate) from PurchaseRequest r "
            + "where r.createdAt >= :from and r.createdAt < :to group by r.status")
    List<Object[]> statusSummary(@Param("from") Instant from, @Param("to") Instant to);
}
