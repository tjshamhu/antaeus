package io.pleo.antaeus.core.services

import io.mockk.every
import io.mockk.mockk
import io.pleo.antaeus.core.exceptions.CurrencyMismatchException
import io.pleo.antaeus.core.exceptions.CustomerNotFoundException
import io.pleo.antaeus.core.exceptions.InvoiceNotFoundException
import io.pleo.antaeus.core.exceptions.NetworkException
import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.data.AntaeusDal
import io.pleo.antaeus.models.FailedSettlement
import io.pleo.antaeus.models.FailedSettlementReason
import io.pleo.antaeus.models.FailedSettlementStatus
import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import io.pleo.antaeus.models.Money

import java.math.BigDecimal

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test


class ReconciliationServiceTest {
    
    private val mockFailedSettlement = FailedSettlement(1, 1, FailedSettlementStatus.UNRESOLVED, 1565010416, FailedSettlementReason.INSUFFICIENT_FUNDS, "TEST LOG", 0)
    private val mockSecondFailedSettlement = FailedSettlement(2, 2, FailedSettlementStatus.UNRESOLVED, 1565010416, FailedSettlementReason.NETWORK_ERROR, "TEST LOG", 0)
    private val mockInvoice = Invoice(1, 10, Money(BigDecimal.valueOf(100.00), Currency.EUR), InvoiceStatus.PENDING)

    private val dal = mockk<AntaeusDal> {
        every { fetchFailedSettlements() } returns listOf (
            mockFailedSettlement,
            mockSecondFailedSettlement
        )
        every { updateInvoice(invoice = any(), status = any()) } returns 1
        every { createFailedSettlement(invoice =  any(), reasonCreated = any(), log = any()) } returns 1
        every { updateFailedSettlement(failedSettlement = any(), log = any(), status = any()) } returns 1
    }

    @Test
    fun `will not charge customer if already charged`() {
        val paymentProvider = mockk<PaymentProvider> {
            every { charge(any()) } returns true
            every { getPaymentStatus(any()) } returns true
        }

        val reconciliationService = ReconciliationService(paymentProvider = paymentProvider, dal = dal)
        
        assertFalse(reconciliationService.attemptResettleInvoice(invoice = mockInvoice, failedSettlement = mockSecondFailedSettlement))
    }

    @Test
    fun `will successfully charge customer if invoice was not already charged`() {
        val paymentProvider = mockk<PaymentProvider> {
            every { charge(any()) } returns true
            every { getPaymentStatus(any()) } returns false
        }

        val reconciliationService = ReconciliationService(paymentProvider = paymentProvider, dal = dal)
        
        assertTrue(reconciliationService.attemptResettleInvoice(invoice = mockInvoice, failedSettlement = mockSecondFailedSettlement))
    }

    @Test
    fun `will not charge customer on CustomerNotFoundException`() {
        val paymentProvider = mockk<PaymentProvider> {
            every { charge(any()) } throws CustomerNotFoundException(404)
        }

        val reconciliationService = ReconciliationService(paymentProvider = paymentProvider, dal = dal)
        
        assertFalse(reconciliationService.attemptResettleInvoice(invoice = mockInvoice, failedSettlement = mockFailedSettlement))
    }

    @Test
    fun `will not charge customer after CurrencyMismatchException`() {
        val paymentProvider = mockk<PaymentProvider> {
            every { charge(any()) } throws CurrencyMismatchException(404, 404)
        }

        val reconciliationService = ReconciliationService(paymentProvider = paymentProvider, dal = dal)
        
        assertFalse(reconciliationService.attemptResettleInvoice(invoice = mockInvoice, failedSettlement = mockFailedSettlement))
    }

}