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

import mu.KotlinLogging


private val logger = KotlinLogging.logger {}

class BillingService (
    private val paymentProvider: PaymentProvider,
    private val dal: AntaeusDal
) {

    fun settleUpaidInvoices(): Unit {
       val unpaidInvoices = dal.fetchUpdaidInvoices()
       for (invoice in unpaidInvoices) {
           settleInvoice(invoice)
       }
    }

    fun settleInvoice(invoice: Invoice): Boolean {
        try {
                val charged = paymentProvider.charge(invoice)
                if (charged) {
                    logger.info { "Invoice [${invoice.id}] successfully charged." }
                    // notify customer, email billing, 
                    dal.updateInvoice(invoice, InvoiceStatus.PAID)
                } else {
                    // notify customer not enough balance
                    logger.info { "Invoice [${invoice.id}] failed to charge because [INSUFFICIENT_FUNDS]." }
                    dal.createFailedSettlement(
                        invoice = invoice, 
                        reasonCreated = FailedSettlementReason.INSUFFICIENT_FUNDS, 
                        log = "Insufficent funds to debit account"
                    )
                    logger.info { "Invoice [${invoice.id}] marked for reconciliation." }
                }
                return charged
            } catch (e: CurrencyMismatchException) {
                // log this, email customer, notify devs
                logger.info { "Invoice [${invoice.id}] failed to because [CURRENCY_MISMATCH]. [${e.toString()}]" }
                dal.createFailedSettlement(
                    invoice = invoice, 
                    reasonCreated = FailedSettlementReason.CURRENCY_MISMATCH, 
                    log = e.toString()
                )
                logger.info { "Invoice [${invoice.id}] marked for reconciliation." }
                return false

            } catch (e: CustomerNotFoundException) {
                // notify devs
                logger.info { "Invoice [${invoice.id}] Customer [${invoice.customerId}] not found." }
                return false

            } catch (e: NetworkException) {
                // log this, notify devs
                logger.info { "Invoice [${invoice.id}] failed to because [NETWORK_ERROR]. [${e.toString()}]" }
                dal.createFailedSettlement(
                    invoice = invoice, 
                    reasonCreated = FailedSettlementReason.NETWORK_ERROR, 
                    log = e.toString()
                )
                logger.info { "Invoice [${invoice.id}] marked for reconciliation." }
                return false

            } catch (e: InvoiceNotFoundException) {
                // log this, notify devs
                logger.info { "Invoice [${invoice.id}] not found. [${e.toString()}]" }
                return false

            } catch (e: Exception) {
                // log this,  notify devs
                logger.info { "General excpetion occured with Invoice [${invoice.id}]. [${e.toString()}]." }
                return false
            }
    }
}