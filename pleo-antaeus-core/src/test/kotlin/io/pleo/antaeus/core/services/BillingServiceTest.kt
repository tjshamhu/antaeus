package io.pleo.antaeus.core.services

import io.mockk.every
import io.mockk.mockk
import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.InvoiceNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import io.pleo.antaeus.models.FailedSettlementReason
import io.pleo.antaeus.models.Money

import java.math.BigDecimal

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BillingServiceTest {

    private val mockInvoice = Invoice(1, 10, Money(BigDecimal.valueOf(100.00), Currency.EUR), InvoiceStatus.PENDING)

    private val dal = mockk<AntaeusDal> {
        every { fetchUpdaidInvoices() } returns listOf (
            mockInvoice,
            Invoice(2, 20, Money(BigDecimal.valueOf(200.25), Currency.USD), InvoiceStatus.PENDING),
            Invoice(3, 30, Money(BigDecimal.valueOf(300.50), Currency.DKK), InvoiceStatus.PENDING)
        )
        every { updateInvoice(invoice = any(), status = any()) } returns 1
        every { createFailedSettlement(invoice =  any(), reasonCreated = any(), log = any()) } returns 1
    }

    @Test
    fun `will successfully charge customer`() {
        val paymentProvider = mockk<PaymentProvider> {
            every { charge(any()) } returns true
        }

        val billingService = BillingService(paymentProvider = paymentProvider, dal = dal)
        
        assertTrue(billingService.settleInvoice(mockInvoice))
    }

    @Test
    fun `will not charge customer on INSUFFICIENT_FUNDS`() {
        val paymentProvider = mockk<PaymentProvider> {
            every { charge(any()) } returns false
        }

        val billingService = BillingService(paymentProvider = paymentProvider, dal = dal)
        
        assertFalse(billingService.settleInvoice(mockInvoice))
    }

    @Test
    fun `will not charge customer on NetworkException`() {
        val paymentProvider = mockk<PaymentProvider> {
            every { charge(any()) } throws NetworkException()
        }

        val billingService = BillingService(paymentProvider = paymentProvider, dal = dal)
        
        assertFalse(billingService.settleInvoice(mockInvoice))
    }

    @Test
    fun `will not charge customer on CustomerNotFoundException`() {
        val paymentProvider = mockk<PaymentProvider> {
            every { charge(any()) } throws CustomerNotFoundException(404)
        }

        val billingService = BillingService(paymentProvider = paymentProvider, dal = dal)
        
        assertFalse(billingService.settleInvoice(mockInvoice))
    }

    @Test
    fun `will not charge customer after InvoiceNotFoundException`() {
        val paymentProvider = mockk<PaymentProvider> {
            every { charge(any()) } throws InvoiceNotFoundException(404)
        }

        val billingService = BillingService(paymentProvider = paymentProvider, dal = dal)
        
        assertFalse(billingService.settleInvoice(mockInvoice))
    }

    @Test
    fun `will not charge customer after CurrencyMismatchException`() {
        val paymentProvider = mockk<PaymentProvider> {
            every { charge(any()) } throws CurrencyMismatchException(404, 404)
        }

        val billingService = BillingService(paymentProvider = paymentProvider, dal = dal)
        
        assertFalse(billingService.settleInvoice(mockInvoice))
    }

}
