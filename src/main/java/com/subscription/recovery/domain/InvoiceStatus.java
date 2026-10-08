package com.subscription.recovery.domain;

/**
 * Lifecycle of an invoice.
 *
 * <pre>
 * PENDING --charge ok--> PAID
 * PENDING --charge fails--> FAILED --retry ok--> RECOVERED
 *                                  --deadline passes--> WRITTEN_OFF
 * </pre>
 *
 * PENDING exists so an invoice is persisted <i>before</i> we call the gateway: if the process crashes
 * between the two, the invoice is not lost and can be charged on the next run.
 */
public enum InvoiceStatus {
    PENDING,
    PAID,
    FAILED,
    RECOVERED,
    WRITTEN_OFF;

    /** Final states: nothing more will happen to the invoice. */
    public boolean isTerminal() {
        return this == PAID || this == RECOVERED || this == WRITTEN_OFF;
    }
}
