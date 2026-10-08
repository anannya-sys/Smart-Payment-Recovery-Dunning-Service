package com.subscription.recovery.repository;

import com.subscription.recovery.domain.PaymentAttempt;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {

    List<PaymentAttempt> findByInvoiceIdOrderByAttemptNumber(Long invoiceId);
}
