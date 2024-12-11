import java.io.File

// Enum for Account Types
enum class AccountType {
    CARD, WALLET, ACCOUNT, UNKNOWN
}

// Data Classes to hold parsed transaction details
data class AccountInfo(
    val type: AccountType,
    val number: String?,
    val name: String? = null
)

data class Balance(
    val available: String?,
    val outstanding: String? = null
)

data class Transaction(
    val type: String?,
    val amount: String?,
    val referenceNo: String?,
    val merchant: String?,
    val currency: String?,
    val date: String?,
    val category: String? // New field for category
)

data class TransactionInfo(
    val account: AccountInfo,
    val balance: Balance?,
    val transaction: Transaction
)

// Class to parse SMS text
class SmsParser {
    fun getTransactionInfo(message: String): TransactionInfo {
        val accountRegex = "ac (\\d{3}\\*\\*\\d{3})".toRegex()
        val amountRegex = "(USD|EUR|GBP|ZAR|JPY|CAD|ZWG|ZWL|\$|\u20AC)\\s?([\\d,]+\\.\\d{2})".toRegex()
        val referenceRegex = "REF:(\\S+)".toRegex()
        val merchantRegex = "REF:\\S+\\s+(.*?)\\s+on \\d{2}-\\w{3}-\\d{2}".toRegex()
        val dateRegex = "on (\\d{2}-\\w{3}-\\d{2})".toRegex()
        val balanceRegex = "Available Balance is (USD|EUR|GBP|ZAR|JPY|CAD|ZWG|ZWL) ([\\d,]+\\.\\d{2})".toRegex()

        val accountNumber = accountRegex.find(message)?.groups?.get(1)?.value
        val amountMatch = amountRegex.find(message)
        val currency = amountMatch?.groups?.get(1)?.value
        val amount = amountMatch?.groups?.get(2)?.value?.replace(",", "")
        val referenceNo = referenceRegex.find(message)?.groups?.get(1)?.value
        val merchant = merchantRegex.find(message)?.groups?.get(1)?.value
        val date = dateRegex.find(message)?.groups?.get(1)?.value
        val balanceMatch = balanceRegex.find(message)
        val balanceCurrency = balanceMatch?.groups?.get(1)?.value
        val availableBalance = balanceMatch?.groups?.get(2)?.value?.replace(",", "")

        val transactionType = when {
            message.contains("Debited", ignoreCase = true) -> "DEBIT"
            message.contains("Credited", ignoreCase = true) -> "CREDIT"
            else -> null
        }

        val category = categorizeTransaction(merchant)

        val accountInfo = AccountInfo(
            type = if (accountNumber != null) AccountType.ACCOUNT else AccountType.UNKNOWN,
            number = accountNumber
        )

        val balance = Balance(available = "$balanceCurrency $availableBalance")

        val transaction = Transaction(
            type = transactionType,
            amount = "$currency $amount",
            referenceNo = referenceNo,
            merchant = merchant,
            currency = currency,
            date = date,
            category = category
        )

        checkTransactionAlerts(transaction)

        return TransactionInfo(
            account = accountInfo,
            balance = balance,
            transaction = transaction
        )
    }

    private fun categorizeTransaction(merchant: String?): String {
        return when {
            merchant == null -> "Unknown"
            merchant.contains("grocery", ignoreCase = true) -> "Groceries"
            merchant.contains("fuel", ignoreCase = true) -> "Fuel"
            merchant.contains("airtime", ignoreCase = true) -> "Airtime"
            merchant.contains("electricity", ignoreCase = true) -> "Utilities"
            merchant.contains("restaurant", ignoreCase = true) -> "Dining"
            merchant.contains("shop", ignoreCase = true) -> "Shopping"
            else -> "Others"
        }
    }

    private fun checkTransactionAlerts(transaction: Transaction) {
        if (transaction.type == "DEBIT" && (transaction.amount?.replace(",", "")?.toDoubleOrNull() ?: 0.0) > 1000) {
            println("ALERT: Large debit transaction detected: ${transaction.amount} from ${transaction.merchant}")
        }
    }
}

fun saveTransactionsToFile(transactions: List<TransactionInfo>) {
    val file = File("transactions.csv")
    if (!file.exists()) {
        file.appendText("Account Type,Account Number,Transaction Type,Amount,Merchant,Date,Category\n")
    }
    transactions.forEach { transaction ->
        val line = "${transaction.account.type},${transaction.account.number},${transaction.transaction.type},${transaction.transaction.amount},${transaction.transaction.merchant},${transaction.transaction.date},${transaction.transaction.category}\n"
        file.appendText(line)
    }
    println("Transaction added to transactions.csv")
}

fun exportReport(transactions: List<TransactionInfo>) {
    val reportFile = File("transaction_report.txt")
    reportFile.writeText("Transaction Report\n\n")
    val totalDebit = transactions.filter { it.transaction.type == "DEBIT" }.sumOf { it.transaction.amount?.replace(",", "")?.toDoubleOrNull() ?: 0.0 }
    val totalCredit = transactions.filter { it.transaction.type == "CREDIT" }.sumOf { it.transaction.amount?.replace(",", "")?.toDoubleOrNull() ?: 0.0 }
    reportFile.appendText("Total Debit: \$${"%.2f".format(totalDebit)}\n")
    reportFile.appendText("Total Credit: \$${"%.2f".format(totalCredit)}\n\n")
    reportFile.appendText("Transaction Details:\n")
    transactions.forEachIndexed { index, transaction ->
        reportFile.appendText("${index + 1}. ${transaction.transaction.type} of ${transaction.transaction.amount} at ${transaction.transaction.merchant} on ${transaction.transaction.date} in category ${transaction.transaction.category}\n")
    }
    println("Transaction report created at transaction_report.txt")
}

fun main() {
    val parser = SmsParser()
    val transactions = mutableListOf<TransactionInfo>()

    while (true) {
        println("Please paste the transaction SMS (or type 'exit' to quit):")
        val userInput = readLine()

        if (userInput != null && userInput.equals("exit", ignoreCase = true)) {
            println("Exiting the program.")
            break
        } else if (!userInput.isNullOrBlank()) {
            val transactionInfo = parser.getTransactionInfo(userInput)

            transactions.add(transactionInfo)

            println("\nParsed Transaction Information:")
            println("Account Info: Type = ${transactionInfo.account.type}, Number = ${transactionInfo.account.number}")
            println("Available Balance: ${transactionInfo.balance?.available}")
            println("Transaction Details: Type = ${transactionInfo.transaction.type}, Amount = ${transactionInfo.transaction.amount}, Reference No = ${transactionInfo.transaction.referenceNo}, Merchant = ${transactionInfo.transaction.merchant}, Date = ${transactionInfo.transaction.date}, Category = ${transactionInfo.transaction.category}")

            saveTransactionsToFile(transactions)
            exportReport(transactions)
        } else {
            println("No input provided or input is empty. Please try again.")
        }
    }
}
