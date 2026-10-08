package com.subscription.recovery.controller;

import com.subscription.recovery.dto.CustomerRequest;
import com.subscription.recovery.dto.CustomerResponse;
import com.subscription.recovery.dto.CustomerUpdateRequest;
import com.subscription.recovery.dto.PageResponse;
import com.subscription.recovery.dto.RiskAssessmentResponse;
import com.subscription.recovery.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Thin HTTP layer: validate input, delegate to the service, choose the status code. No business logic here. */
@RestController
@RequestMapping("/api/customers")
@Tag(name = "Customers")
public class CustomerController {

    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Create a customer")
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CustomerRequest request) {
        CustomerResponse created = service.create(request);
        // 201 Created + Location header pointing at the new resource.
        return ResponseEntity.created(URI.create("/api/customers/" + created.id())).body(created);
    }

    @GetMapping
    @Operation(summary = "List customers (paged)")
    public PageResponse<CustomerResponse> list(@PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC)
                                               Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a customer")
    public CustomerResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a customer's name and email")
    public CustomerResponse update(@PathVariable Long id, @Valid @RequestBody CustomerUpdateRequest request) {
        return service.update(id, request);
    }

    @GetMapping("/{id}/risk")
    @Operation(summary = "Churn-risk score (0-100) with the factors behind it")
    public RiskAssessmentResponse risk(@PathVariable Long id) {
        return service.risk(id);
    }
}
