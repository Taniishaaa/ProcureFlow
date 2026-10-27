package com.procureflow.order;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PurchaseOrderRepository
        extends JpaRepository<PurchaseOrder, Long>, JpaSpecificationExecutor<PurchaseOrder> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select po from PurchaseOrder po where po.id = :id")
    Optional<PurchaseOrder> findByIdForUpdate(@Param("id") Long id);

    List<PurchaseOrder> findBySupplierIdOrderByCreatedAtDescIdDesc(Long supplierId);

    /** The live (non-cancelled) order for a request, if any. */
    Optional<PurchaseOrder> findFirstByRequestIdAndStatusNotOrderByIdDesc(Long requestId,
                                                                          PurchaseOrderStatus status);

    long countByStatusIn(Collection<PurchaseOrderStatus> statuses);

    long countByRequestRequesterIdAndStatusIn(Long requesterId, Collection<PurchaseOrderStatus> statuses);

    /** Returns null when there are no matching rows. */
    @Query("select sum(po.totalAmount) from PurchaseOrder po where po.status in :statuses")
    BigDecimal sumTotalByStatusIn(@Param("statuses") Collection<PurchaseOrderStatus> statuses);

    @Query("select po.status, count(po) from PurchaseOrder po group by po.status")
    List<Object[]> statusCounts();

    // ---- reporting -------------------------------------------------------------------------------------------

    @Query("select c.name, sum(po.totalAmount), count(po) from PurchaseOrder po "
            + "join po.request r join r.category c "
            + "where po.status in :statuses and po.issuedAt >= :from and po.issuedAt < :to "
            + "group by c.name order by sum(po.totalAmount) desc")
    List<Object[]> spendByCategory(@Param("statuses") Collection<PurchaseOrderStatus> statuses,
                                   @Param("from") Instant from, @Param("to") Instant to);

    @Query("select s.name, sum(po.totalAmount), count(po) from PurchaseOrder po "
            + "join po.supplier s "
            + "where po.status in :statuses and po.issuedAt >= :from and po.issuedAt < :to "
            + "group by s.name order by sum(po.totalAmount) desc")
    List<Object[]> spendBySupplier(@Param("statuses") Collection<PurchaseOrderStatus> statuses,
                                   @Param("from") Instant from, @Param("to") Instant to);

    @Query("select po.status, count(po), sum(po.totalAmount) from PurchaseOrder po "
            + "where po.createdAt >= :from and po.createdAt < :to group by po.status")
    List<Object[]> statusSummary(@Param("from") Instant from, @Param("to") Instant to);

    @Query("select po.issuedAt, po.totalAmount from PurchaseOrder po "
            + "where po.status in :statuses and po.issuedAt >= :from and po.issuedAt < :to")
    List<Object[]> issuedAmounts(@Param("statuses") Collection<PurchaseOrderStatus> statuses,
                                 @Param("from") Instant from, @Param("to") Instant to);
}
