package com.procureflow.config;

import com.procureflow.audit.AuditService;
import com.procureflow.category.Category;
import com.procureflow.category.CategoryRepository;
import com.procureflow.common.Pricing;
import com.procureflow.notification.NotificationService;
import com.procureflow.notification.NotificationType;
import com.procureflow.order.Delivery;
import com.procureflow.order.DeliveryLine;
import com.procureflow.order.DeliveryRepository;
import com.procureflow.order.PurchaseOrder;
import com.procureflow.order.PurchaseOrderItem;
import com.procureflow.order.PurchaseOrderRepository;
import com.procureflow.order.PurchaseOrderStatus;
import com.procureflow.request.Approval;
import com.procureflow.request.ApprovalDecision;
import com.procureflow.request.ApprovalRepository;
import com.procureflow.request.PurchaseRequest;
import com.procureflow.request.PurchaseRequestItem;
import com.procureflow.request.PurchaseRequestRepository;
import com.procureflow.request.RequestStatus;
import com.procureflow.supplier.Quotation;
import com.procureflow.supplier.QuotationLine;
import com.procureflow.supplier.QuotationRepository;
import com.procureflow.supplier.QuotationStatus;
import com.procureflow.supplier.Supplier;
import com.procureflow.supplier.SupplierRepository;
import com.procureflow.user.Role;
import com.procureflow.user.User;
import com.procureflow.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.UUID;

/**
 * Seeds demo accounts, categories and suppliers into an EMPTY database (and optionally a few sample records
 * so every page has something to show). It never touches a database that already has users.
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private record ItemSpec(String name, String description, int quantity, String unitPrice) {
    }

    private final UserRepository users;
    private final CategoryRepository categories;
    private final SupplierRepository suppliers;
    private final PurchaseRequestRepository requests;
    private final ApprovalRepository approvals;
    private final QuotationRepository quotations;
    private final PurchaseOrderRepository orders;
    private final DeliveryRepository deliveries;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final NotificationService notifications;
    private final PlatformTransactionManager transactionManager;
    private final String password;
    private final boolean sampleData;

    public DataSeeder(UserRepository users, CategoryRepository categories, SupplierRepository suppliers,
                      PurchaseRequestRepository requests, ApprovalRepository approvals,
                      QuotationRepository quotations, PurchaseOrderRepository orders,
                      DeliveryRepository deliveries, PasswordEncoder encoder, AuditService audit,
                      NotificationService notifications, PlatformTransactionManager transactionManager,
                      @Value("${app.seed.password}") String password,
                      @Value("${app.seed.sample-data:false}") boolean sampleData) {
        this.users = users;
        this.categories = categories;
        this.suppliers = suppliers;
        this.requests = requests;
        this.approvals = approvals;
        this.quotations = quotations;
        this.orders = orders;
        this.deliveries = deliveries;
        this.encoder = encoder;
        this.audit = audit;
        this.notifications = notifications;
        this.transactionManager = transactionManager;
        this.password = password;
        this.sampleData = sampleData;
    }

    @Override
    public void run(String... args) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> seed());
    }

    private void seed() {
        if (users.count() > 0) {
            log.info("Database already contains users - skipping demo data");
            return;
        }
        User admin = user("admin@procureflow.com", "Aarav Admin", "IT Administration", Role.ADMIN);
        User manager = user("manager@procureflow.com", "Meera Manager", "Operations", Role.MANAGER);
        User employee = user("employee@procureflow.com", "Esha Employee", "Engineering", Role.EMPLOYEE);
        User employee2 = user("employee2@procureflow.com", "Rohan Employee", "Marketing", Role.EMPLOYEE);
        User officer = user("procurement@procureflow.com", "Priya Procurement", "Procurement",
                Role.PROCUREMENT_OFFICER);

        Category itHardware = category("IT Hardware", "Laptops, monitors, networking and peripherals");
        Category software = category("Software & Licenses", "Subscriptions and perpetual licenses");
        Category officeSupplies = category("Office Supplies", "Stationery and consumables");
        Category furniture = category("Furniture", "Desks, chairs and storage");
        Category facilities = category("Facilities & Maintenance", "Repairs, cleaning and equipment");
        Category marketing = category("Marketing Services", "Design, print and campaigns");

        Supplier techSource = supplier("TechSource Distributors", "Karan Mehta", "sales@techsource.example",
                "+91 22 5550 1100", "Andheri East, Mumbai", "27AAATT1234A1Z5", itHardware, software);
        Supplier nimbus = supplier("Nimbus Computers", "Anita Rao", "quotes@nimbus.example", "+91 80 5550 2200",
                "Whitefield, Bengaluru", "29AAACN5678B1Z2", itHardware);
        Supplier officeWorld = supplier("OfficeWorld Supplies", "Sunil Shah", "orders@officeworld.example",
                "+91 79 5550 3300", "Navrangpura, Ahmedabad", "24AAACO9012C1Z8", officeSupplies, furniture);
        Supplier ergo = supplier("Ergo Furnishings", "Neha Kapoor", "hello@ergofurnishings.example",
                "+91 11 5550 4400", "Okhla, New Delhi", "07AAACE3456D1Z1", furniture);
        supplier("BrightSpark Facilities", "Vikram Nair", "service@brightspark.example", "+91 44 5550 5500",
                "Guindy, Chennai", "33AAACB7890E1Z9", facilities);
        supplier("Pixel & Print Studio", "Isha Verma", "studio@pixelprint.example", "+91 20 5550 6600",
                "Koregaon Park, Pune", "27AAACP1234F1Z4", marketing);
        Supplier legacy = supplier("Legacy Traders", "Ramesh Iyer", "info@legacytraders.example",
                "+91 33 5550 7700", "Salt Lake, Kolkata", "19AAACL5678G1Z7", officeSupplies);
        legacy.setActive(false);

        log.info("Seeded demo accounts (password from app.seed.password), categories and suppliers");
        if (!sampleData) {
            return;
        }

        // ---- A: approved request with two competing quotations (ready for the comparison page) ---------------
        PurchaseRequest laptops = request(employee, itHardware, "Developer laptops for new hires",
                "Five engineers join next month and need standard-issue machines.", RequestStatus.APPROVED,
                new ItemSpec("Laptop 15-inch, 16 GB RAM", "Business-grade, 3-year warranty", 5, "85000"),
                new ItemSpec("USB-C docking station", "Dual display support", 5, "9500"));
        approval(laptops, manager, ApprovalDecision.APPROVED, "Approved - within the hiring budget.");
        quotation(laptops, techSource, officer, 18, 10, "Net 30", QuotationStatus.SUBMITTED, "82000", "9800");
        quotation(laptops, nimbus, officer, 18, 6, "Net 15", QuotationStatus.SUBMITTED, "84500", "9000");

        // ---- B: submitted request waiting for the manager ------------------------------------------------------
        PurchaseRequest chairs = request(employee2, furniture, "Ergonomic chairs for the marketing floor",
                "Current chairs are damaged and several staff report back pain.", RequestStatus.SUBMITTED,
                new ItemSpec("Ergonomic office chair", "Adjustable lumbar support", 10, "12000"));
        notifications.notifyUser(manager, NotificationType.APPROVAL, "Approval needed",
                employee2.getFullName() + " submitted " + chairs.getRequestNumber() + ": " + chairs.getTitle(),
                "/approvals/" + chairs.getId());

        // ---- C: draft ------------------------------------------------------------------------------------------
        request(employee, officeSupplies, "Team notebooks and pens", "Quarterly stationery restock.",
                RequestStatus.DRAFT,
                new ItemSpec("A5 notebook", "Ruled, 200 pages", 40, "120"),
                new ItemSpec("Ballpoint pen (box of 50)", "Blue ink", 4, "350"));

        // ---- D: ordered request with an issued purchase order and one partial delivery -------------------------
        PurchaseRequest monitors = request(employee, itHardware, "Monitors for the design team",
                "Colour-accurate displays are needed for the redesign project.", RequestStatus.ORDERED,
                new ItemSpec("27-inch 4K monitor", "IPS panel, factory calibrated", 8, "22000"));
        approval(monitors, manager, ApprovalDecision.APPROVED, "Go ahead.");
        Quotation chosen = quotation(monitors, nimbus, officer, 18, 14, "Net 30", QuotationStatus.SELECTED,
                "21500");
        quotation(monitors, techSource, officer, 18, 20, "Net 45", QuotationStatus.REJECTED, "22800");
        PurchaseOrder po = purchaseOrder(monitors, chosen, officer);
        Delivery delivery = new Delivery();
        delivery.setPurchaseOrder(po);
        delivery.setReceivedDate(LocalDate.now().minusDays(1));
        delivery.setNotes("First shipment, boxes undamaged");
        delivery.setReceivedBy(officer);
        PurchaseOrderItem poItem = po.getItems().get(0);
        poItem.setQuantityReceived(3);
        DeliveryLine line = new DeliveryLine();
        line.setPoItem(poItem);
        line.setQuantity(3);
        delivery.addLine(line);
        deliveries.save(delivery);
        po.setStatus(PurchaseOrderStatus.PARTIALLY_RECEIVED);
        audit.logAs(officer.getId(), officer.getEmail(), "RECEIVE_DELIVERY", "PURCHASE_ORDER", po.getId(),
                "Received 3 unit(s) against " + po.getPoNumber() + "; order is now PARTIALLY_RECEIVED");
        notifications.notifyUser(employee, NotificationType.DELIVERY, "Partial delivery received",
                po.getPoNumber() + " (" + monitors.getRequestNumber() + "): " + po.progressPercent()
                        + "% received.", "/orders/" + po.getId());

        // ---- E: rejected request -------------------------------------------------------------------------------
        PurchaseRequest coffee = request(employee2, facilities, "Premium coffee machine",
                "Nice to have for the marketing lounge.", RequestStatus.REJECTED,
                new ItemSpec("Bean-to-cup coffee machine", "Commercial grade", 1, "145000"));
        approval(coffee, manager, ApprovalDecision.REJECTED, "Not within this quarter's budget.");
        notifications.notifyUser(employee2, NotificationType.APPROVAL, "Request rejected",
                coffee.getRequestNumber() + " was rejected: Not within this quarter's budget.",
                "/requests/" + coffee.getId());

        audit.logAs(admin.getId(), admin.getEmail(), "SEED", "SYSTEM", null, "Loaded sample data");
        log.info("Seeded sample requests, quotations, purchase order and delivery");
    }

    // ---- builders ------------------------------------------------------------------------------------------

    private User user(String email, String name, String department, Role role) {
        User u = new User();
        u.setEmail(email);
        u.setFullName(name);
        u.setDepartment(department);
        u.setRole(role);
        u.setPasswordHash(encoder.encode(password));
        u.setActive(true);
        return users.save(u);
    }

    private Category category(String name, String description) {
        Category c = new Category();
        c.setName(name);
        c.setDescription(description);
        return categories.save(c);
    }

    private Supplier supplier(String name, String contact, String email, String phone, String address,
                              String taxId, Category... supplied) {
        Supplier s = new Supplier();
        s.setName(name);
        s.setContactName(contact);
        s.setEmail(email);
        s.setPhone(phone);
        s.setAddress(address);
        s.setTaxId(taxId);
        s.getCategories().addAll(List.of(supplied));
        return suppliers.save(s);
    }

    private PurchaseRequest request(User requester, Category category, String title, String justification,
                                    RequestStatus status, ItemSpec... specs) {
        PurchaseRequest r = new PurchaseRequest();
        r.setRequestNumber("TMP-" + UUID.randomUUID());
        r.setRequester(requester);
        r.setCategory(category);
        r.setTitle(title);
        r.setJustification(justification);
        r.setStatus(status);
        for (ItemSpec spec : specs) {
            PurchaseRequestItem item = new PurchaseRequestItem();
            item.setName(spec.name());
            item.setDescription(spec.description());
            item.setQuantity(spec.quantity());
            item.setEstimatedUnitPrice(new BigDecimal(spec.unitPrice()));
            r.addItem(item);
        }
        r.recalcTotal();
        if (status != RequestStatus.DRAFT) {
            r.setSubmittedAt(Instant.now().minus(Duration.ofDays(3)));
        }
        r = requests.saveAndFlush(r);
        r.setRequestNumber(String.format("PR-%d-%06d", Year.now().getValue(), r.getId()));
        return r;
    }

    private void approval(PurchaseRequest r, User approver, ApprovalDecision decision, String comments) {
        Approval a = new Approval();
        a.setRequest(r);
        a.setApprover(approver);
        a.setDecision(decision);
        a.setComments(comments);
        a.setDecidedAt(Instant.now().minus(Duration.ofDays(2)));
        approvals.save(a);
    }

    private Quotation quotation(PurchaseRequest r, Supplier supplier, User by, int taxPercent, int days,
                                String terms, QuotationStatus status, String... unitPrices) {
        Quotation q = new Quotation();
        q.setRequest(r);
        q.setSupplier(supplier);
        q.setCreatedBy(by);
        q.setStatus(status);
        q.setDeliveryDays(days);
        q.setPaymentTerms(terms);
        q.setValidUntil(LocalDate.now().plusDays(30));
        BigDecimal subtotal = BigDecimal.ZERO;
        for (int i = 0; i < r.getItems().size(); i++) {
            PurchaseRequestItem item = r.getItems().get(i);
            QuotationLine line = new QuotationLine();
            line.setRequestItem(item);
            line.setUnitPrice(new BigDecimal(unitPrices[i]));
            q.addLine(line);
            subtotal = subtotal.add(Pricing.lineTotal(item.getQuantity(), line.getUnitPrice()));
        }
        Pricing.Totals totals = Pricing.totals(subtotal, BigDecimal.valueOf(taxPercent));
        q.setTaxPercent(BigDecimal.valueOf(taxPercent).setScale(2));
        q.setSubtotal(totals.subtotal());
        q.setTaxAmount(totals.tax());
        q.setTotalAmount(totals.total());
        return quotations.save(q);
    }

    private PurchaseOrder purchaseOrder(PurchaseRequest r, Quotation q, User creator) {
        PurchaseOrder po = new PurchaseOrder();
        po.setPoNumber("TMP-" + UUID.randomUUID());
        po.setRequest(r);
        po.setSupplier(q.getSupplier());
        po.setQuotation(q);
        po.setStatus(PurchaseOrderStatus.ISSUED);
        po.setSubtotal(q.getSubtotal());
        po.setTaxPercent(q.getTaxPercent());
        po.setTaxAmount(q.getTaxAmount());
        po.setTotalAmount(q.getTotalAmount());
        po.setDeliveryDays(q.getDeliveryDays());
        po.setPaymentTerms(q.getPaymentTerms());
        po.setCreatedBy(creator);
        po.setIssuedAt(Instant.now().minus(Duration.ofDays(2)));
        po.setExpectedDeliveryDate(LocalDate.now().plusDays(q.getDeliveryDays() - 2L));
        for (QuotationLine line : q.getLines()) {
            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setName(line.getRequestItem().getName());
            item.setDescription(line.getRequestItem().getDescription());
            item.setQuantityOrdered(line.getRequestItem().getQuantity());
            item.setUnitPrice(line.getUnitPrice());
            po.addItem(item);
        }
        po = orders.saveAndFlush(po);
        po.setPoNumber(String.format("PO-%d-%06d", Year.now().getValue(), po.getId()));
        audit.logAs(creator.getId(), creator.getEmail(), "CREATE", "PURCHASE_ORDER", po.getId(),
                "Created " + po.getPoNumber() + " for " + r.getRequestNumber() + " from '"
                        + q.getSupplier().getName() + "'");
        audit.logAs(creator.getId(), creator.getEmail(), "ISSUE", "PURCHASE_ORDER", po.getId(),
                "Issued " + po.getPoNumber() + " to '" + q.getSupplier().getName() + "'");
        return po;
    }
}
