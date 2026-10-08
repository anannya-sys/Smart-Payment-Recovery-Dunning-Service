package com.subscription.recovery.controller;

import com.subscription.recovery.domain.InvoiceStatus;
import com.subscription.recovery.dto.InvoiceResponse;
import com.subscription.recovery.dto.InvoiceSummaryResponse;
import com.subscription.recovery.dto.PageResponse;
import com.subscription.recovery.service.InvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invoices")
@Tag(name = "Invoices")
public class InvoiceController {

    private final InvoiceService service;

    public InvoiceController(InvoiceService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List invoices, optionally filtered by one or more statuses (e.g. ?status=FAILED)")
    public PageResponse<InvoiceSummaryResponse> list(
            @RequestParam(required = false) Set<InvoiceStatus> status,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(status, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an invoice with its full payment-attempt history and retry decisions")
    public InvoiceResponse get(@PathVariable Long id) {
        return service.get(id);
    }
}
