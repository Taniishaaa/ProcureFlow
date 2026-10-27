package com.procureflow.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    List<Delivery> findByPurchaseOrderIdOrderByReceivedDateDescIdDesc(Long purchaseOrderId);
}
