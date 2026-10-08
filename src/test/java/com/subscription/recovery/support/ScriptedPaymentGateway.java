package com.subscription.recovery.support;

import com.subscription.recovery.domain.FailureReason;
import com.subscription.recovery.gateway.ChargeRequest;
import com.subscription.recovery.gateway.PaymentGateway;
import com.subscription.recovery.gateway.PaymentResult;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/** Test double: returns pre-programmed results in order (success when the script is empty) and records calls. */
public class ScriptedPaymentGateway implements PaymentGateway {

    private final Deque<Optional<FailureReason>> script = new ArrayDeque<>();
    private final List<ChargeRequest> requests = new ArrayList<>();

    /** Queue outcomes; {@code null} means success. */
    public void willReturn(FailureReason... outcomes) {
        for (FailureReason r : outcomes) {
            script.add(Optional.ofNullable(r));
        }
    }

    public List<ChargeRequest> requests() {
        return requests;
    }

    public void reset() {
        script.clear();
        requests.clear();
    }

    @Override
    public PaymentResult charge(ChargeRequest request) {
        requests.add(request);
        Optional<FailureReason> next = script.isEmpty() ? Optional.empty() : script.poll();
        return next.map(r -> PaymentResult.failure(r, "TEST-" + request.invoiceId()))
                .orElseGet(() -> PaymentResult.success("TEST-" + request.invoiceId()));
    }
}
