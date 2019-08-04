package io.pleo.antaeus.core.jobs

import io.pleo.antaeus.core.external.PaymentProvider
import io.pleo.antaeus.core.services.ReconciliationService
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

@CronTrigger(cron = "0 0/5 * * * ?")  // Runs every 5 minutes
class ReconciliationJob () : Job() {

    override fun doRun(): Unit {
        logger.info { "Reconciliation Job is starting." }

        // Create database connection
        val db = Database.connect("jdbc:sqlite:/tmp/data.db", "org.sqlite.JDBC")
        .also { TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE }

        // Set up data access layer.
        val dal = AntaeusDal(db = db)

        // Get third parties
        val paymentProvider = getPaymentProvider()

        val reconciliationService = ReconciliationService(paymentProvider = paymentProvider, dal = dal)

        reconciliationService.retryFailedSettlements()

    }
}