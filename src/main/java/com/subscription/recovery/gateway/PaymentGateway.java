package com.subscription.recovery.gateway;

/**
 * Port to the outside payment world (Razorpay, Cashfree, PayU, ...). The rest of the code depends only on this
 * interface, so swapping the simulator for a real provider is a new implementation, not a rewrite
 * (Dependency Inversion / hexagonal "port and adapter").
 */
public interface PaymentGateway {

    PaymentResult charge(ChargeRequest request);
}
