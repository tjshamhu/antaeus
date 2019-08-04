package io.pleo.antaeus.core.services

import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.InvoiceNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.FailedSettlement
import io.pleo.antaeus.models.FailedSettlementReason
import io.pleo.antaeus.models.FailedSettlementStatus
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus

import java.lang.Math
import java.time.Instant
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}


class ReconciliationService (
    private val paymentProvider: PaymentProvider,
    private val dal: AntaeusDal
) {

    fun retryFailedSettlements(): Unit {

        val failedSettlements = dal.fetchFailedSettlements()
        for (failedSettlement in failedSettlements) {
            try {
                val invoice = dal.fetchInvoice(failedSettlement.invoiceId) as Invoice
                if (hasWaited(failedSettlement)) {
                    attemptResettleInvoice(failedSettlement = failedSettlement, invoice = invoice)
                }
            } catch (e: InvoiceNotFoundException) {
                logger.info { "Invoice [${failedSettlement.invoiceId}] not found. [${e.toString()}]" }
            } 
        }
    }

    fun attemptResettleInvoice(failedSettlement: FailedSettlement, invoice: Invoice): Boolean {
        try {
            var invoiceIsPaid = false;

            // if the failedSettlement was created due to a network error, we do not know whether it charged or not, we ought to check
            if (failedSettlement.reasonCreated == FailedSettlementReason.NETWORK_ERROR) {
                invoiceIsPaid = paymentProvider.getPaymentStatus(invoice)
            }

            if (invoiceIsPaid) {
                dal.updateInvoice(invoice, InvoiceStatus.PAID)
                dal.updateFailedSettlement(
                    failedSettlement = failedSettlement, 
                    log = failedSettlement.log, 
                    status = FailedSettlementStatus.RESOLVED 
                )
                logger.info { "FailedSettlement [${failedSettlement.id}] verified as successfully charged. Marking as resolved. Total attempts: [${failedSettlement.retries + 1}]" }
                return false

            } else {
                logger.info { "FailedSettlement [${failedSettlement.id}] verified as not charged. Total attempts: [${failedSettlement.retries + 1}]" }
                val charged = paymentProvider.charge(invoice)
                if (charged) {
                    // log invoice paid, notify customer, email billing,
                    dal.updateInvoice(invoice, InvoiceStatus.PAID)
                    dal.updateFailedSettlement(
                        failedSettlement = failedSettlement, 
                        log = failedSettlement.log, 
                        status = FailedSettlementStatus.RESOLVED 
                    )
                    logger.info { "Invoice [${invoice.id}] successfully charged. FailedSettlement [${failedSettlement.id}] resolved. Total attempts: [${failedSettlement.retries + 1}]" }

                } else {
                    // notify customer not enough balance
                    dal.updateFailedSettlement(
                        failedSettlement = failedSettlement, 
                        log = "Insufficent funds to debit account", 
                        status = failedSettlement.status 
                    )
                    logger.info { "Invoice [${invoice.id}] of FailedSettlement [${failedSettlement.id}] failed to charge because [INSUFFICIENT_FUNDS]. Total attempts: [${failedSettlement.retries + 1}]" }
                }
                return charged
            }
                
        } catch (e: CurrencyMismatchException) {
            // email customer, notify devs, create charge attempt
            dal.updateFailedSettlement(
                failedSettlement = failedSettlement, 
                log = e.toString(), 
                status = failedSettlement.status 
            )
            logger.info { "Invoice [${invoice.id}] of FailedSettlement [${failedSettlement.id}] failed to charge because [CURRENCY_MISMATCH]. [${e.toString()}]. Total attempts: [${failedSettlement.retries + 1}]" }
            return false
        
        } catch (e: CustomerNotFoundException) {
            // Notify devs
            dal.updateFailedSettlement(
                failedSettlement = failedSettlement, 
                log = e.toString(), 
                status = failedSettlement.status
            )
            logger.info { "Invoice [${invoice.id}] of FailedSettlement [${failedSettlement.id}], Customer [${invoice.customerId}] not found. Total attempts: [${failedSettlement.retries + 1}]" }
            return false
        
        } catch (e: NetworkException) {
            // Notify devs
            logger.info { "Invoice [${invoice.id}] of FailedSettlement [${failedSettlement.id}] failed to charge because [NETWORK_ERROR]. [${e.toString()}]. Total attempts: [${failedSettlement.retries + 1}]" }
            dal.updateFailedSettlement(
                failedSettlement = failedSettlement, 
                log = e.toString(), 
                status = failedSettlement.status
            )
            return false
        
        } catch (e: Exception) {
            // Notify devs
            logger.info { "General excpetion occured with Invoice [${invoice.id}] of FailedSettlement [${failedSettlement.id}]. [${e.toString()}]." }
            return false
        }
    }

    fun hasWaited(failedSettlement: FailedSettlement): Boolean {
        /*
            this fun returns true if a certain amount of time has passed since an invoice's last charge attempt.
            we calculate the waiting period by doing 5 to the power number_of_retries + 1.
            If the invoice has been retried twice, it ought to wait 125 minutes. i.e: 5 ** 3
        */ 
        val now = Instant.now().getEpochSecond()
        val timeToWait = Math.pow(5.toDouble(), (failedSettlement.retries + 1).toDouble()) * 60  // 5 minutes to power # of ++retries, times 60 seconds
        val timeNotToExceed = Math.pow(5.toDouble(), (failedSettlement.retries + 2).toDouble()) * 60
        return now >= timeToWait && now < timeNotToExceed
    }
}