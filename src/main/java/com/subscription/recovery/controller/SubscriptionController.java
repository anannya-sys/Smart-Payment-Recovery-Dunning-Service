package com.subscription.recovery.controller;

import com.subscription.recovery.domain.SubscriptionStatus;
import com.subscription.recovery.dto.InvoiceResponse;
import com.subscription.recovery.dto.PageResponse;
import com.subscription.recovery.dto.PaymentMethodUpdateRequest;
import com.subscription.recovery.dto.StatusChangeRequest;
import com.subscription.recovery.dto.SubscriptionRequest;
import com.subscription.recovery.dto.SubscriptionResponse;
import com.subscription.recovery.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/subscriptions")
@Tag(name = "Subscriptions")
public class SubscriptionController {

    private final SubscriptionService service;

    public SubscriptionController(SubscriptionService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Subscribe a customer to a plan")
    public ResponseEntity<SubscriptionResponse> create(@Valid @RequestBody SubscriptionRequest request) {
        SubscriptionResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/subscriptions/" + created.id())).body(created);
    }

    @GetMapping
    @Operation(summary = "List subscriptions, optionally filtered by status")
    public PageResponse<SubscriptionResponse> list(
            @RequestParam(required = false) SubscriptionStatus status,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return service.list(status, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a subscription")
    public SubscriptionResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Pause, resume or cancel a subscription (validated by the state machine)")
    public SubscriptionResponse changeStatus(@PathVariable Long id, @Valid @RequestBody StatusChangeRequest request) {
        return service.changeStatus(id, request);
    }

    @PutMapping("/{id}/payment-method")
    @Operation(summary = "Customer updated their mandate/card; failed invoices are retried right away")
    public SubscriptionResponse updatePaymentMethod(@PathVariable Long id,
                                                    @Valid @RequestBody PaymentMethodUpdateRequest request) {
        return service.updatePaymentMethod(id, request);
    }

    @GetMapping("/{id}/invoices")
    @Operation(summary = "Invoices of a subscription, newest first")
    public List<InvoiceResponse> invoices(@PathVariable Long id) {
        return service.invoices(id);
    }
}
