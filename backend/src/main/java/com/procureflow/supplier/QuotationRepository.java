package com.procureflow.supplier;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface QuotationRepository extends JpaRepository<Quotation, Long>, JpaSpecificationExecutor<Quotation> {

    List<Quotation> findByRequestId(Long requestId);

    boolean existsByRequestIdAndSupplierId(Long requestId, Long supplierId);

    List<Quotation> findBySupplierIdOrderByCreatedAtDescIdDesc(Long supplierId);

    @Query("select q.request.id, count(q) from Quotation q where q.request.id in :requestIds group by q.request.id")
    List<Object[]> countByRequestIds(@Param("requestIds") Collection<Long> requestIds);
}
