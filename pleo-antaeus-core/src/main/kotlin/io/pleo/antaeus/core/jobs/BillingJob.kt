package io.pleo.antaeus.core.jobs

import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.core.services.BillingService
import io.pleo.antaeus.core.utils.getPaymentProvider
import io.pleo.antaeus.data.AntaeusDal

import mu.KotlinLogging

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager

import org.knowm.sundial.Job;
import org.knowm.sundial.annotations.CronTrigger;
import org.knowm.sundial.exceptions.JobInterruptException;

import java.sql.Connection

private val logger = KotlinLogging.logger {}

@CronTrigger(cron = "0 0 4 1 * ?")  // Runs every 1st day of every month at 4:00am
class BillingJob () : Job() {

    override fun doRun(): Unit {
        logger.info { "Billing Job is starting." }

        val db = Database.connect("jdbc:sqlite:/tmp/data.db", "org.sqlite.JDBC")
        .also { TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE }

        // Create database connection
        val dal = AntaeusDal(db = db)

        // Get third parties
        val paymentProvider = getPaymentProvider()

        val billingService = BillingService(paymentProvider = paymentProvider, dal = dal)

        billingService.settleUpaidInvoices()

    }
}