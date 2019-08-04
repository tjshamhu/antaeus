/*
    Defines mappings between database rows and Kotlin objects.
    To be used by `AntaeusDal`.
 */

package io.pleo.antaeus.data

import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.Customer
import io.pleo.antaeus.models.FailedSettlement
import io.pleo.antaeus.models.FailedSettlementStatus
import io.pleo.antaeus.models.FailedSettlementReason
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import io.pleo.antaeus.models.Money
import org.jetbrains.exposed.sql.ResultRow

fun ResultRow.toInvoice(): Invoice = Invoice(
    id = this[InvoiceTable.id],
    amount = Money(
        value = this[InvoiceTable.value],
        currency = Currency.valueOf(this[InvoiceTable.currency])
    ),
    status = InvoiceStatus.valueOf(this[InvoiceTable.status]),
    customerId = this[InvoiceTable.customerId]
)

fun ResultRow.toCustomer(): Customer = Customer(
    id = this[CustomerTable.id],
    currency = Currency.valueOf(this[CustomerTable.currency])
)

fun ResultRow.toFailedSettlement(): FailedSettlement = FailedSettlement(
    id = this[FailedSettlementTable.id],
    invoiceId = this[FailedSettlementTable.invoiceId],
    status = FailedSettlementStatus.valueOf(this[FailedSettlementTable.status]),
    dateCreated = this[FailedSettlementTable.dateCreated],
    reasonCreated = FailedSettlementReason.valueOf(this[FailedSettlementTable.reasonCreated]),
    log = this[FailedSettlementTable.log],
    retries = this[FailedSettlementTable.retries]
)