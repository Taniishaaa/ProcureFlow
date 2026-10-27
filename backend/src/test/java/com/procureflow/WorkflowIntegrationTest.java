package com.procureflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests through the real HTTP layer, security filter chain, services and an in-memory database.
 * They exercise the business rules the requirements call out: approval gating, role checks, quantity limits.
 */
@SpringBootTest(properties = {
        "app.seed.enabled=true",
        "app.seed.sample-data=false",
        "app.dev-expose-reset-token=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class WorkflowIntegrationTest {

    private static final String PASSWORD = "Password@123";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper mapper;

    // ------------------------------------------------------------------------------------------------------
    // Full procure-to-pay workflow
    // ------------------------------------------------------------------------------------------------------

    @Test
    void requestToDeliveryHappyPathAndGuards() throws Exception {
        String employee = login("employee@procureflow.com");
        String manager = login("manager@procureflow.com");
        String officer = login("procurement@procureflow.com");
        String admin = login("admin@procureflow.com");

        long categoryId = categoryId(employee, "IT Hardware");
        long techSource = supplierId(officer, "TechSource");
        long nimbus = supplierId(officer, "Nimbus");

        // 1. Employee creates and submits a request for 2 x 50,000.
        JsonNode request = send(HttpMethod.POST, "/api/requests", employee, Map.of(
                "title", "Workflow test laptops",
                "categoryId", categoryId,
                "justification", "Integration test",
                "items", List.of(Map.of("name", "Laptop", "description", "15 inch", "quantity", 2,
                        "estimatedUnitPrice", 50000)),
                "submit", true), 201);
        long requestId = request.get("id").asLong();
        assertThat(request.get("status").asText()).isEqualTo("SUBMITTED");
        assertThat(request.get("requestNumber").asText()).startsWith("PR-");
        assertThat(new BigDecimal(request.get("totalEstimate").asText())).isEqualByComparingTo("100000");

        // 2. A purchase order cannot be created from an unapproved request, even by calling the API directly.
        send(HttpMethod.POST, "/api/purchase-orders", officer,
                Map.of("requestId", requestId, "quotationId", 999999), 409);

        // 3. Quotations cannot be recorded before approval either.
        long itemId = request.get("items").get(0).get("id").asLong();
        Map<String, Object> quoteBody = quotationBody(requestId, techSource, 18, 10, itemId, 48000);
        send(HttpMethod.POST, "/api/quotations", officer, quoteBody, 409);

        // 4. Employees cannot approve; the manager can, once.
        send(HttpMethod.POST, "/api/requests/" + requestId + "/approve", employee, Map.of("comments", "me"), 403);
        JsonNode approved = send(HttpMethod.POST, "/api/requests/" + requestId + "/approve", manager,
                Map.of("comments", "Looks fine"), 200);
        assertThat(approved.get("status").asText()).isEqualTo("APPROVED");
        send(HttpMethod.POST, "/api/requests/" + requestId + "/approve", manager, Map.of("comments", "again"), 409);

        // 5. Procurement records two quotations (totals include tax).
        JsonNode q1 = send(HttpMethod.POST, "/api/quotations", officer, quoteBody, 201);
        assertThat(new BigDecimal(q1.get("subtotal").asText())).isEqualByComparingTo("96000");
        assertThat(new BigDecimal(q1.get("totalAmount").asText())).isEqualByComparingTo("113280");
        send(HttpMethod.POST, "/api/quotations", officer, quoteBody, 409); // duplicate supplier for this request

        JsonNode q2 = send(HttpMethod.POST, "/api/quotations", officer,
                quotationBody(requestId, nimbus, 18, 5, itemId, 47000), 201);

        // 6. The comparison recommends the cheaper and faster offer.
        JsonNode comparison = send(HttpMethod.GET, "/api/requests/" + requestId + "/comparison", officer, null, 200);
        JsonNode rows = comparison.get("rows");
        assertThat(rows).hasSize(2);
        JsonNode best = rows.get(0);
        assertThat(best.get("quotationId").asLong()).isEqualTo(q2.get("id").asLong());
        assertThat(best.get("recommended").asBoolean()).isTrue();
        assertThat(best.get("lowestPrice").asBoolean()).isTrue();
        assertThat(best.get("fastestDelivery").asBoolean()).isTrue();

        // 7. Create the purchase order from the chosen quotation; a second one is refused.
        JsonNode po = send(HttpMethod.POST, "/api/purchase-orders", officer,
                Map.of("requestId", requestId, "quotationId", q2.get("id").asLong()), 201);
        long poId = po.get("id").asLong();
        assertThat(po.get("status").asText()).isEqualTo("DRAFT");
        assertThat(po.get("poNumber").asText()).startsWith("PO-");
        assertThat(new BigDecimal(po.get("totalAmount").asText())).isEqualByComparingTo("110920");
        send(HttpMethod.POST, "/api/purchase-orders", officer,
                Map.of("requestId", requestId, "quotationId", q1.get("id").asLong()), 409);

        // Employees cannot see a draft order; once issued they can.
        send(HttpMethod.GET, "/api/purchase-orders/" + poId, employee, null, 403);
        JsonNode issued = send(HttpMethod.POST, "/api/purchase-orders/" + poId + "/issue", officer, null, 200);
        assertThat(issued.get("status").asText()).isEqualTo("ISSUED");
        assertThat(issued.get("expectedDeliveryDate").asText())
                .isEqualTo(LocalDate.now().plusDays(5).toString());
        send(HttpMethod.GET, "/api/purchase-orders/" + poId, employee, null, 200);

        // 8. Deliveries: over-receiving is refused, partial then final delivery completes the order.
        long poItemId = issued.get("items").get(0).get("id").asLong();
        String today = LocalDate.now().toString();
        send(HttpMethod.POST, "/api/purchase-orders/" + poId + "/deliveries", officer,
                delivery(today, poItemId, 3), 400);
        JsonNode partial = send(HttpMethod.POST, "/api/purchase-orders/" + poId + "/deliveries", officer,
                delivery(today, poItemId, 1), 201);
        assertThat(partial.get("status").asText()).isEqualTo("PARTIALLY_RECEIVED");
        assertThat(partial.get("progressPercent").asInt()).isEqualTo(50);
        JsonNode complete = send(HttpMethod.POST, "/api/purchase-orders/" + poId + "/deliveries", officer,
                delivery(today, poItemId, 1), 201);
        assertThat(complete.get("status").asText()).isEqualTo("COMPLETED");
        send(HttpMethod.POST, "/api/purchase-orders/" + poId + "/deliveries", officer,
                delivery(today, poItemId, 1), 409);
        send(HttpMethod.POST, "/api/purchase-orders/" + poId + "/cancel", officer, Map.of("reason", "late"), 409);

        JsonNode finalRequest = send(HttpMethod.GET, "/api/requests/" + requestId, employee, null, 200);
        assertThat(finalRequest.get("status").asText()).isEqualTo("FULFILLED");
        assertThat(finalRequest.get("purchaseOrderNumber").asText()).isEqualTo(po.get("poNumber").asText());

        // 9. The lifecycle is recorded, and the employee was notified along the way.
        JsonNode history = send(HttpMethod.GET, "/api/purchase-orders/" + poId + "/history", officer, null, 200);
        List<String> actions = new ArrayList<>();
        history.forEach(h -> actions.add(h.get("action").asText()));
        assertThat(actions).containsExactly("CREATE", "ISSUE", "RECEIVE_DELIVERY", "RECEIVE_DELIVERY");

        JsonNode notifications = send(HttpMethod.GET, "/api/notifications", employee, null, 200);
        assertThat(notifications.get("totalElements").asLong()).isGreaterThanOrEqualTo(3);

        JsonNode audit = send(HttpMethod.GET, "/api/audit-logs?entityType=PURCHASE_REQUEST&action=APPROVE", admin,
                null, 200);
        assertThat(audit.get("totalElements").asLong()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void rejectionNeedsAReasonAndReturnsRequestToRequester() throws Exception {
        String employee = login("employee@procureflow.com");
        String manager = login("manager@procureflow.com");
        long categoryId = categoryId(employee, "Office Supplies");

        JsonNode request = send(HttpMethod.POST, "/api/requests", employee, Map.of(
                "title", "Rejection test", "categoryId", categoryId, "justification", "test",
                "items", List.of(Map.of("name", "Pens", "quantity", 10, "estimatedUnitPrice", 20)),
                "submit", true), 201);
        long id = request.get("id").asLong();

        send(HttpMethod.POST, "/api/requests/" + id + "/reject", manager, Map.of("comments", "  "), 400);
        JsonNode rejected = send(HttpMethod.POST, "/api/requests/" + id + "/reject", manager,
                Map.of("comments", "Over budget"), 200);
        assertThat(rejected.get("status").asText()).isEqualTo("REJECTED");
        assertThat(rejected.get("approvals").get(0).get("comments").asText()).isEqualTo("Over budget");

        // The employee can fix and resubmit a rejected request.
        JsonNode edited = send(HttpMethod.PUT, "/api/requests/" + id, employee, Map.of(
                "title", "Rejection test (revised)", "categoryId", categoryId, "justification", "revised",
                "items", List.of(Map.of("name", "Pens", "quantity", 5, "estimatedUnitPrice", 20))), 200);
        assertThat(edited.get("status").asText()).isEqualTo("DRAFT");
        JsonNode resubmitted = send(HttpMethod.POST, "/api/requests/" + id + "/submit", employee, null, 200);
        assertThat(resubmitted.get("status").asText()).isEqualTo("SUBMITTED");
        assertThat(resubmitted.get("approvals")).hasSize(1);
    }

    @Test
    void employeesCannotTouchOtherEmployeesRequests() throws Exception {
        String employee = login("employee@procureflow.com");
        String other = login("employee2@procureflow.com");
        long categoryId = categoryId(employee, "Furniture");

        JsonNode request = send(HttpMethod.POST, "/api/requests", employee, Map.of(
                "title", "Private request", "categoryId", categoryId, "justification", "test",
                "items", List.of(Map.of("name", "Desk", "quantity", 1, "estimatedUnitPrice", 9000))), 201);
        long id = request.get("id").asLong();

        send(HttpMethod.GET, "/api/requests/" + id, other, null, 403);
        send(HttpMethod.POST, "/api/requests/" + id + "/cancel", other, null, 403);
        // A manager cannot see someone's private draft either.
        send(HttpMethod.GET, "/api/requests/" + id, login("manager@procureflow.com"), null, 403);
        // The owner can cancel it.
        JsonNode cancelled = send(HttpMethod.POST, "/api/requests/" + id + "/cancel", employee, null, 200);
        assertThat(cancelled.get("status").asText()).isEqualTo("CANCELLED");
    }

    // ------------------------------------------------------------------------------------------------------
    // Authentication and authorisation
    // ------------------------------------------------------------------------------------------------------

    @Test
    void protectedEndpointsRequireAuthenticationAndTheRightRole() throws Exception {
        send(HttpMethod.GET, "/api/requests/mine", null, null, 401);
        send(HttpMethod.GET, "/api/requests/mine", "not-a-real-token", null, 401);

        String employee = login("employee@procureflow.com");
        send(HttpMethod.GET, "/api/users", employee, null, 403);
        send(HttpMethod.GET, "/api/suppliers", employee, null, 403);
        send(HttpMethod.GET, "/api/audit-logs", employee, null, 403);
        send(HttpMethod.GET, "/api/requests/pending-approval", employee, null, 403);
        send(HttpMethod.GET, "/api/requests/mine", employee, null, 200);

        String manager = login("manager@procureflow.com");
        send(HttpMethod.GET, "/api/purchase-orders", manager, null, 403);
        // Only employees create requests (the body is valid, so the role check is what rejects it).
        send(HttpMethod.POST, "/api/requests", manager, Map.of(
                "title", "Not allowed", "categoryId", 1, "justification", "test",
                "items", List.of(Map.of("name", "Pens", "quantity", 1, "estimatedUnitPrice", 10))), 403);
    }

    @Test
    void wrongPasswordIsRejectedWithoutRevealingWhichPartWasWrong() throws Exception {
        JsonNode body = send(HttpMethod.POST, "/api/auth/login", null,
                Map.of("email", "employee@procureflow.com", "password", "Wrong@1234"), 401);
        assertThat(body.get("message").asText()).isEqualTo("Invalid email or password");
        JsonNode unknown = send(HttpMethod.POST, "/api/auth/login", null,
                Map.of("email", "nobody@procureflow.com", "password", "Wrong@1234"), 401);
        assertThat(unknown.get("message").asText()).isEqualTo("Invalid email or password");
    }

    @Test
    void validationErrorsListTheOffendingFields() throws Exception {
        String employee = login("employee@procureflow.com");
        JsonNode body = send(HttpMethod.POST, "/api/requests", employee, Map.of(
                "title", "", "categoryId", 1, "justification", "x", "items", List.of()), 400);
        assertThat(body.get("fieldErrors").has("title")).isTrue();
        assertThat(body.get("fieldErrors").has("items")).isTrue();
    }

    @Test
    void deactivatedUsersLoseAccessImmediately() throws Exception {
        String admin = login("admin@procureflow.com");
        JsonNode created = send(HttpMethod.POST, "/api/users", admin, Map.of(
                "email", "temp.user@procureflow.com", "fullName", "Temp User", "department", "QA",
                "role", "EMPLOYEE", "password", "Temp@12345"), 201);
        long id = created.get("id").asLong();
        send(HttpMethod.POST, "/api/users", admin, Map.of(
                "email", "temp.user@procureflow.com", "fullName", "Dup", "role", "EMPLOYEE",
                "password", "Temp@12345"), 409);

        JsonNode login = send(HttpMethod.POST, "/api/auth/login", null,
                Map.of("email", "temp.user@procureflow.com", "password", "Temp@12345"), 200);
        String token = login.get("token").asText();
        send(HttpMethod.GET, "/api/me", token, null, 200);

        send(HttpMethod.PATCH, "/api/users/" + id + "/active", admin, Map.of("active", false), 200);
        send(HttpMethod.GET, "/api/me", token, null, 401);
        send(HttpMethod.POST, "/api/auth/login", null,
                Map.of("email", "temp.user@procureflow.com", "password", "Temp@12345"), 403);

        // Administrators cannot lock themselves out.
        long adminId = send(HttpMethod.GET, "/api/me", admin, null, 200).get("id").asLong();
        send(HttpMethod.PATCH, "/api/users/" + adminId + "/active", admin, Map.of("active", false), 400);
    }

    @Test
    void passwordResetFlowWorksOnce() throws Exception {
        String admin = login("admin@procureflow.com");
        send(HttpMethod.POST, "/api/users", admin, Map.of(
                "email", "reset.user@procureflow.com", "fullName", "Reset User", "role", "EMPLOYEE",
                "password", "Old@12345"), 201);

        JsonNode forgot = send(HttpMethod.POST, "/api/auth/forgot-password", null,
                Map.of("email", "reset.user@procureflow.com"), 200);
        String token = forgot.get("resetToken").asText();
        assertThat(token).isNotBlank();

        // Unknown accounts get the same answer (no token), so emails cannot be enumerated.
        JsonNode unknown = send(HttpMethod.POST, "/api/auth/forgot-password", null,
                Map.of("email", "ghost@procureflow.com"), 200);
        assertThat(unknown.get("message").asText()).isEqualTo(forgot.get("message").asText());
        assertThat(unknown.has("resetToken") && !unknown.get("resetToken").isNull()).isFalse();

        send(HttpMethod.POST, "/api/auth/reset-password", null,
                Map.of("token", token, "newPassword", "weak"), 400);
        send(HttpMethod.POST, "/api/auth/reset-password", null,
                Map.of("token", token, "newPassword", "New@12345"), 204);
        send(HttpMethod.POST, "/api/auth/reset-password", null,
                Map.of("token", token, "newPassword", "Another@123"), 400); // single use
        send(HttpMethod.POST, "/api/auth/login", null,
                Map.of("email", "reset.user@procureflow.com", "password", "New@12345"), 200);
        send(HttpMethod.POST, "/api/auth/login", null,
                Map.of("email", "reset.user@procureflow.com", "password", "Old@12345"), 401);
    }

    // ------------------------------------------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------------------------------------------

    private Map<String, Object> quotationBody(long requestId, long supplierId, int tax, int days, long itemId,
                                              int unitPrice) {
        return Map.of(
                "requestId", requestId,
                "supplierId", supplierId,
                "taxPercent", tax,
                "deliveryDays", days,
                "paymentTerms", "Net 30",
                "lines", List.of(Map.of("requestItemId", itemId, "unitPrice", unitPrice)));
    }

    private Map<String, Object> delivery(String date, long poItemId, int quantity) {
        return Map.of("receivedDate", date, "notes", "test",
                "lines", List.of(Map.of("poItemId", poItemId, "quantity", quantity)));
    }

    private long categoryId(String token, String name) throws Exception {
        for (JsonNode c : send(HttpMethod.GET, "/api/categories", token, null, 200)) {
            if (c.get("name").asText().equals(name)) {
                return c.get("id").asLong();
            }
        }
        throw new AssertionError("Category not found: " + name);
    }

    private long supplierId(String officerToken, String search) throws Exception {
        JsonNode page = send(HttpMethod.GET, "/api/suppliers?search=" + search, officerToken, null, 200);
        return page.get("content").get(0).get("id").asLong();
    }

    private String login(String email) throws Exception {
        JsonNode body = send(HttpMethod.POST, "/api/auth/login", null,
                Map.of("email", email, "password", PASSWORD), 200);
        return body.get("token").asText();
    }

    private JsonNode send(HttpMethod method, String url, String token, Object body, int expectedStatus)
            throws Exception {
        MockHttpServletRequestBuilder builder = request(method, url);
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        if (body != null) {
            builder.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
        } else if (method == HttpMethod.POST || method == HttpMethod.PUT) {
            builder.contentType(MediaType.APPLICATION_JSON);
        }
        MvcResult result = mvc.perform(builder).andExpect(status().is(expectedStatus)).andReturn();
        String content = result.getResponse().getContentAsString();
        return content == null || content.isBlank() ? null : mapper.readTree(content);
    }
}
