package io.pleo.antaeus.models

enum class FailedSettlementStatus {
    UNRESOLVED,
    RESOLVED
}

enum class FailedSettlementReason {
    CURRENCY_MISMATCH,
    INSUFFICIENT_FUNDS,
    NETWORK_ERROR,
}

data class FailedSettlement(
    val id: Int,
    val invoiceId: Int,
    val status: FailedSettlementStatus,
    val dateCreated: Long,
    val reasonCreated: FailedSettlementReason,
    val log: String,
    val retries: Int
)
