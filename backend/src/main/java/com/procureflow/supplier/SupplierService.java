package com.procureflow.supplier;

import com.procureflow.audit.AuditService;
import com.procureflow.category.Category;
import com.procureflow.category.CategoryRepository;
import com.procureflow.common.ApiException;
import com.procureflow.common.PageResponse;
import com.procureflow.common.Paging;
import com.procureflow.order.PurchaseOrderRepository;
import com.procureflow.supplier.SupplierDtos.OrderBrief;
import com.procureflow.supplier.SupplierDtos.QuotationBrief;
import com.procureflow.supplier.SupplierDtos.SupplierDetail;
import com.procureflow.supplier.SupplierDtos.SupplierDto;
import com.procureflow.supplier.SupplierDtos.SupplierInput;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class SupplierService {

    private final SupplierRepository suppliers;
    private final CategoryRepository categories;
    private final QuotationRepository quotations;
    private final PurchaseOrderRepository orders;
    private final AuditService audit;

    public SupplierService(SupplierRepository suppliers, CategoryRepository categories,
                           QuotationRepository quotations, PurchaseOrderRepository orders, AuditService audit) {
        this.suppliers = suppliers;
        this.categories = categories;
        this.quotations = quotations;
        this.orders = orders;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<SupplierDto> list(String search, Boolean active, Long categoryId, int page, int size) {
        Specification<Supplier> spec = (root, query, cb) -> cb.conjunction();
        if (StringUtils.hasText(search)) {
            String like = "%" + search.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("name")), like),
                    cb.like(cb.lower(root.get("contactName")), like),
                    cb.like(cb.lower(root.get("email")), like)));
        }
        if (active != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("active"), active));
        }
        if (categoryId != null) {
            spec = spec.and((root, query, cb) -> {
                query.distinct(true);
                return cb.equal(root.join("categories").get("id"), categoryId);
            });
        }
        Page<Supplier> result = suppliers.findAll(spec, Paging.of(page, size, Sort.by(Sort.Direction.ASC, "name")));
        return PageResponse.of(result, SupplierDto::from);
    }

    @Transactional(readOnly = true)
    public SupplierDetail get(Long id) {
        Supplier s = load(id);
        List<QuotationBrief> quotationList = quotations.findBySupplierIdOrderByCreatedAtDescIdDesc(id).stream()
                .map(q -> new QuotationBrief(q.getId(), q.getRequest().getId(), q.getRequest().getRequestNumber(),
                        q.getTotalAmount(), q.getDeliveryDays(), q.getStatus(), q.getCreatedAt()))
                .toList();
        List<OrderBrief> orderList = orders.findBySupplierIdOrderByCreatedAtDescIdDesc(id).stream()
                .map(o -> new OrderBrief(o.getId(), o.getPoNumber(), o.getStatus().name(), o.getTotalAmount(),
                        o.getIssuedAt(), o.getCreatedAt()))
                .toList();
        return new SupplierDetail(SupplierDto.from(s), quotationList, orderList);
    }

    @Transactional
    public SupplierDto create(SupplierInput in) {
        String name = in.name().trim();
        if (suppliers.existsByNameIgnoreCase(name)) {
            throw ApiException.conflict("A supplier named '" + name + "' already exists");
        }
        Supplier s = new Supplier();
        apply(s, in);
        s = suppliers.save(s);
        audit.log("CREATE", "SUPPLIER", s.getId(), "Created supplier '" + s.getName() + "'");
        return SupplierDto.from(s);
    }

    @Transactional
    public SupplierDto update(Long id, SupplierInput in) {
        Supplier s = load(id);
        String name = in.name().trim();
        if (suppliers.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw ApiException.conflict("A supplier named '" + name + "' already exists");
        }
        apply(s, in);
        audit.log("UPDATE", "SUPPLIER", s.getId(), "Updated supplier '" + s.getName() + "'");
        return SupplierDto.from(s);
    }

    @Transactional
    public SupplierDto setActive(Long id, boolean active) {
        Supplier s = load(id);
        s.setActive(active);
        audit.log(active ? "ACTIVATE" : "DEACTIVATE", "SUPPLIER", s.getId(),
                (active ? "Activated" : "Deactivated") + " supplier '" + s.getName() + "'");
        return SupplierDto.from(s);
    }

    private void apply(Supplier s, SupplierInput in) {
        List<Category> found = categories.findAllById(in.categoryIds());
        if (found.size() != in.categoryIds().size()) {
            throw ApiException.badRequest("One or more categories do not exist");
        }
        s.setName(in.name().trim());
        s.setContactName(in.contactName().trim());
        s.setEmail(in.email().trim().toLowerCase());
        s.setPhone(blankToNull(in.phone()));
        s.setAddress(blankToNull(in.address()));
        s.setTaxId(blankToNull(in.taxId()));
        s.getCategories().clear();
        s.getCategories().addAll(found);
    }

    private Supplier load(Long id) {
        return suppliers.findById(id).orElseThrow(() -> ApiException.notFound("Supplier", id));
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
