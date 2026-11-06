package za.co.eci.slipmanager.data

import org.json.JSONObject

data class CardSlipSummary(
    val id: String, val clientUuid: String, val supplier: String, val date: String,
    val amountCents: Long, val purpose: String, val project: String,
    val status: String, val documentAvailable: Boolean
)
data class CardLedgerSummary(
    val id: String, val date: String, val amountCents: Long,
    val type: String, val reference: String, val note: String
)
data class CardFundingCorrection(
    val id: String, val date: String, val amountCents: Long, val reason: String, val alreadyIncluded: Boolean
)
data class CompanyCardActivity(
    val cardId: String, val name: String, val reference: String, val balanceCents: Long,
    val generatedAt: String, val slips: List<CardSlipSummary>, val ledger: List<CardLedgerSummary>,
    val corrections: List<CardFundingCorrection>, val requests: List<CompanyCardFundingRequest>,
    val updates: List<CompanyCardUpdateRequest>, val fromCache: Boolean = false
) {
    companion object {
        fun parse(raw: String, fromCache: Boolean = false): CompanyCardActivity {
            val root = JSONObject(raw)
            val card = root.getJSONObject("card")
            fun text(o: JSONObject, key: String) = if (o.isNull(key)) "" else o.optString(key)
            fun amount(o: JSONObject, key: String) = text(o, key).toLongOrNull() ?: 0L
            val slips = buildList {
                val rows = root.optJSONArray("slips")
                if (rows != null) for (i in 0 until rows.length()) rows.getJSONObject(i).let { row ->
                    add(CardSlipSummary(row.getString("id"), text(row,"client_uuid"), row.getString("supplier"),
                        text(row,"purchase_date").ifBlank { text(row,"created_at").take(10) },
                        amount(row,"card_funded_cents"),text(row,"purpose"),text(row,"project_name"),
                        text(row,"review_status"), !row.isNull("document_id")))
                }
            }
            val ledger = buildList {
                val rows = root.optJSONArray("ledger")
                if (rows != null) for (i in 0 until rows.length()) rows.getJSONObject(i).let { row ->
                    add(CardLedgerSummary(row.getString("id"),text(row,"entry_date"),amount(row,"amount_cents"),
                        text(row,"entry_type"),text(row,"reference"),text(row,"note")))
                }
            }
            val corrections = buildList {
                val rows = root.optJSONArray("fundingCorrections")
                if (rows != null) for (i in 0 until rows.length()) rows.getJSONObject(i).let { row ->
                    add(CardFundingCorrection(row.getString("action_id"),text(row,"entry_date"),amount(row,"amount_cents"),
                        text(row,"reason"),text(row,"credit_mode") == "ALREADY_RECORDED"))
                }
            }
            val requests = buildList {
                val rows = root.optJSONArray("requests")
                if (rows != null) for (i in 0 until rows.length()) rows.getJSONObject(i).let { row ->
                    add(CompanyCardFundingRequest(row.getString("id"),card.getString("id"),card.getString("name"),
                        amount(row,"requested_cents"),text(row,"approved_cents").toLongOrNull(),text(row,"purpose"),text(row,"status"),text(row,"submitted_at")))
                }
            }
            val updates = buildList {
                val rows = root.optJSONArray("updateRequests")
                if (rows != null) for (i in 0 until rows.length()) rows.getJSONObject(i).let { row ->
                    add(CompanyCardUpdateRequest(id=row.getString("id"),cardName=card.getString("name"),cardId=card.getString("id"),
                        message=text(row,"message"),bankBalanceCents=text(row,"bank_balance_cents").toLongOrNull(),requestedAt=text(row,"requested_at"),acknowledged=!row.isNull("acknowledged_at"),resolved=!row.isNull("resolved_at")))
                }
            }
            return CompanyCardActivity(card.getString("id"),card.getString("name"),text(card,"card_reference"),
                amount(card,"balance_cents"),text(root,"generatedAt"),slips,ledger,corrections,requests,updates,fromCache)
        }
    }
}
