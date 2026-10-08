package com.subscription.recovery.service;

import com.subscription.recovery.domain.Invoice;
import com.subscription.recovery.domain.InvoiceStatus;
import com.subscription.recovery.dto.DtoMapper;
import com.subscription.recovery.dto.InvoiceResponse;
import com.subscription.recovery.dto.InvoiceSummaryResponse;
import com.subscription.recovery.dto.PageResponse;
import com.subscription.recovery.exception.ResourceNotFoundException;
import com.subscription.recovery.repository.InvoiceRepository;
import com.subscription.recovery.repository.PaymentAttemptRepository;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side for invoices: an invoice together with its full attempt history. */
@Service
@Transactional(readOnly = true)
public class InvoiceService {

    private final InvoiceRepository invoices;
    private final PaymentAttemptRepository attempts;

    public InvoiceService(InvoiceRepository invoices, PaymentAttemptRepository attempts) {
        this.invoices = invoices;
        this.attempts = attempts;
    }

    /** Invoices in the given statuses (all statuses when empty), newest first by default. */
    public PageResponse<InvoiceSummaryResponse> list(Set<InvoiceStatus> statuses, Pageable pageable) {
        Set<InvoiceStatus> filter = statuses == null || statuses.isEmpty()
                ? EnumSet.allOf(InvoiceStatus.class) : statuses;
        return PageResponse.of(invoices.findByStatusIn(filter, pageable), DtoMapper::toSummary);
    }

    public InvoiceResponse get(Long id) {
        Invoice invoice = invoices.findById(id).orElseThrow(() -> new ResourceNotFoundException("Invoice", id));
        return DtoMapper.toResponse(invoice, attempts.findByInvoiceIdOrderByAttemptNumber(id));
    }
}
