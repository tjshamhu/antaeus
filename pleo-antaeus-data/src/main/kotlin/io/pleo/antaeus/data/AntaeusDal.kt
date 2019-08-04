/*
    Implements the data access layer (DAL).
    This file implements the database queries used to fetch and insert rows in our database tables.

    See the `mappings` module for the conversions between database rows and Kotlin objects.
 */

package io.pleo.antaeus.data

import io.pleo.antaeus.models.Currency
import io.pleo.antaeus.models.Customer
import io.pleo.antaeus.models.FailedSettlement
import io.pleo.antaeus.models.FailedSettlementReason
import io.pleo.antaeus.models.FailedSettlementStatus
import io.pleo.antaeus.models.Invoice
import io.pleo.antaeus.models.InvoiceStatus
import io.pleo.antaeus.models.Money

import java.time.Instant

import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.transactions.transaction

private const val MAX_RETRIES = 3

class AntaeusDal(private val db: Database) {
    fun fetchInvoice(id: Int): Invoice? {
        // transaction(db) runs the internal query as a new database transaction.
        return transaction(db) {
            // Returns the first invoice with matching id.
            InvoiceTable
                .select { InvoiceTable.id.eq(id) }
                .firstOrNull()
                ?.toInvoice()
        }
    }

    fun fetchInvoices(): List<Invoice> {
        return transaction(db) {
            InvoiceTable
                .selectAll()
                .map { it.toInvoice() }
        }
    }

    fun createInvoice(amount: Money, customer: Customer, status: InvoiceStatus = InvoiceStatus.PENDING): Invoice? {
        val id = transaction(db) {
            // Insert the invoice and returns its new id.
            InvoiceTable
                .insert {
                    it[this.value] = amount.value
                    it[this.currency] = amount.currency.toString()
                    it[this.status] = status.toString()
                    it[this.customerId] = customer.id
                } get InvoiceTable.id
        }

        return fetchInvoice(id!!)
    }

    fun fetchCustomer(id: Int): Customer? {
        return transaction(db) {
            CustomerTable
                .select { CustomerTable.id.eq(id) }
                .firstOrNull()
                ?.toCustomer()
        }
    }

    fun fetchCustomers(): List<Customer> {
        return transaction(db) {
            CustomerTable
                .selectAll()
                .map { it.toCustomer() }
        }
    }

    fun createCustomer(currency: Currency): Customer? {
        val id = transaction(db) {
            // Insert the customer and return its new id.
            CustomerTable.insert {
                it[this.currency] = currency.toString()
            } get CustomerTable.id
        }

        return fetchCustomer(id!!)
    }

    fun createFailedSettlement(invoice: Invoice, reasonCreated: FailedSettlementReason, log: String): Int? {
        val id = transaction(db) {
            // creates a new charge attempt item and returns its new id.
            FailedSettlementTable
                .insert {
                    it[this.invoiceId] = invoice.id
                    it[this.reasonCreated] = reasonCreated.toString()
                    it[this.log] = log
                    it[this.dateCreated] = Instant.now().getEpochSecond()
                } get FailedSettlementTable.id
        }
        return id!!
    }

    fun updateFailedSettlement(failedSettlement: FailedSettlement, log: String?, status: FailedSettlementStatus?): Int {
        transaction(db) {
            FailedSettlementTable.update({ FailedSettlementTable.id eq failedSettlement.id }) {
                with(SqlExpressionBuilder) {
                    it.update(FailedSettlementTable.retries, FailedSettlementTable.retries + 1)
                }
            }
        }
        val id = transaction(db) {
            // updates a failed settlement depending on the parameters passed
            FailedSettlementTable.update({ FailedSettlementTable.id eq failedSettlement.id }) {
                it[this.status] = status.toString()
                it[this.log] = log.toString()
            }
        }
        return id!!
    }

    fun fetchFailedSettlements(): List<FailedSettlement> {
        // will select all charge attempt which have not exceeded MAX_RETRIES == 3
        return transaction(db) {
            FailedSettlementTable
                .select { (FailedSettlementTable.retries less MAX_RETRIES) and (FailedSettlementTable.status eq FailedSettlementStatus.UNRESOLVED.toString()) }
                .map { it.toFailedSettlement() }
        }
    }
}
