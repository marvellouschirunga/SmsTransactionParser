import java.io.File

// Enum for Account Types
// Represents different types of accounts associated with transactions
enum class AccountType {
    CARD, WALLET, ACCOUNT, UNKNOWN
}

// Data Classes to hold parsed transaction details
// Holds account-related information
data class AccountInfo(
    val type: AccountType, // Type of account (CARD, WALLET, etc.)
    val number: String?,   // Account number, can be null
    val name: String? = null // Optional account name
)

// Holds balance-related information
data class Balance(
    val available: String?,        // Available balance
    val outstanding: String? = null // Outstanding balance, optional
)

// Holds transaction-specific details
data class Transaction(
    val type: String?,      // Transaction type (DEBIT/CREDIT)
    val amount: String?,    // Transaction amount
    val referenceNo: String?, // Reference number for the transaction
    val merchant: String?,  // Merchant or description of the transaction
    val currency: String?,  // Currency type (e.g., USD)
    val date: String?,      // Transaction date
    val category: String?   // Category of the transaction (e.g., Groceries, Fuel)
)

// Combines account, balance, and transaction details
data class TransactionInfo(
    val account: AccountInfo, // Account information
    val balance: Balance?,    // Balance information
    val transaction: Transaction // Transaction details
)

// Class to parse SMS text into structured transaction information (LOGIC)
class SmsParser {
    // Parses a transaction SMS message into a TransactionInfo object
    fun getTransactionInfo(message: String): TransactionInfo {
        // Regular expressions to extract transaction details
        val accountRegex = "ac (\\d{3}\\*\\*\\d{3})".toRegex() // Extracts masked account number
        val amountRegex = "(USD|EUR|GBP|ZAR|JPY|CAD|ZWG|ZWL|\\$|\\u20AC)\\s?([\\d,]+\\.\\d{2})".toRegex() // Extracts amount and currency
        val referenceRegex = "REF:(\\S+)".toRegex() // Extracts reference number
        val merchantRegex = "REF:\\S+\\s+(.*?)\\s+on \\d{2}-\\w{3}-\\d{2}".toRegex() // Extracts merchant name
        val dateRegex = "on (\\d{2}-\\w{3}-\\d{2})".toRegex() // Extracts transaction date
        val balanceRegex = "Available Balance is (USD|EUR|GBP|ZAR|JPY|CAD|ZWG|ZWL) ([\\d,]+\\.\\d{2})".toRegex() // Extracts available balance

        // Extract values from the SMS message using the regular expressions
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

        // Determine if the transaction is a debit or credit
        val transactionType = when {
            message.contains("Debited", ignoreCase = true) -> "DEBIT"
            message.contains("Credited", ignoreCase = true) -> "CREDIT"
            else -> null
        }

        // Categorize the transaction based on the merchant
        val category = categorizeTransaction(merchant)

        // Create objects for account, balance, and transaction details
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

        // Check for large debit transactions and alert the user
        checkTransactionAlerts(transaction)

        // Return the structured transaction information
        return TransactionInfo(
            account = accountInfo,
            balance = balance,
            transaction = transaction
        )
    }

    // Categorizes the transaction based on merchant name
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

    // Checks for large debit transactions and prints an alert
    private fun checkTransactionAlerts(transaction: Transaction) {
        if (transaction.type == "DEBIT" && (transaction.amount?.replace(",", "")?.toDoubleOrNull() ?: 0.0) > 1000) {
            println("ALERT: Large debit transaction detected: ${transaction.amount} from ${transaction.merchant}")
        }
    }
}

// Saves a list of transactions to a CSV file
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

// Generates a transaction report and saves it to a text file
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

// Main function to run the SMS parser
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

            // Print parsed transaction details
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