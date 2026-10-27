package com.procureflow.request;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface ApprovalRepository extends JpaRepository<Approval, Long>, JpaSpecificationExecutor<Approval> {

    List<Approval> findByRequestIdOrderByDecidedAtAscIdAsc(Long requestId);

    long countByApproverIdAndDecision(Long approverId, ApprovalDecision decision);
}
