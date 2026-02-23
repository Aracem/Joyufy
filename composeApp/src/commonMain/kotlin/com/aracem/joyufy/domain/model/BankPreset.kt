package com.aracem.joyufy.domain.model

data class BankPreset(
    val name: String,
    val logoUrl: String,
    val defaultColor: String,
    val type: AccountType,
)

private fun clearbit(domain: String) = "https://logo.clearbit.com/$domain"

val BankPresets: List<BankPreset> = listOf(
    // ── Bancos ────────────────────────────────────────────────────────────────
    BankPreset("Santander",   clearbit("santander.com"),      "#EC0000", AccountType.BANK),
    BankPreset("BBVA",        clearbit("bbva.es"),             "#004481", AccountType.BANK),
    BankPreset("CaixaBank",   clearbit("caixabank.es"),        "#0066CC", AccountType.BANK),
    BankPreset("Bankinter",   clearbit("bankinter.com"),       "#FF6600", AccountType.BANK),
    BankPreset("Sabadell",    clearbit("bancsabadell.com"),    "#006BA6", AccountType.BANK),
    BankPreset("ING",         clearbit("ing.es"),              "#FF6200", AccountType.BANK),
    BankPreset("Unicaja",     clearbit("unicajabanco.es"),     "#006633", AccountType.BANK),
    BankPreset("Kutxabank",   clearbit("kutxabank.es"),        "#E30613", AccountType.BANK),
    BankPreset("Abanca",      clearbit("abanca.com"),          "#00A651", AccountType.BANK),
    BankPreset("Openbank",    clearbit("openbank.es"),         "#FF0000", AccountType.BANK),
    BankPreset("N26",         clearbit("n26.com"),             "#00C6A7", AccountType.BANK),
    BankPreset("Revolut",     clearbit("revolut.com"),         "#191C1F", AccountType.BANK),
    // ── Inversión ─────────────────────────────────────────────────────────────
    BankPreset("Indexa Capital",  clearbit("indexacapital.com"),  "#1A1A2E", AccountType.INVESTMENT),
    BankPreset("MyInvestor",      clearbit("myinvestor.es"),       "#FF4F00", AccountType.INVESTMENT),
    BankPreset("inbestMe",        clearbit("inbestme.com"),         "#00C389", AccountType.INVESTMENT),
    BankPreset("Finizens",        clearbit("finizens.com"),         "#1565C0", AccountType.INVESTMENT),
    BankPreset("Renta 4",         clearbit("r4.com"),               "#E4032E", AccountType.INVESTMENT),
    BankPreset("Self Bank",       clearbit("selfbank.es"),          "#005B99", AccountType.INVESTMENT),
    BankPreset("DEGIRO",          clearbit("degiro.com"),           "#FF6600", AccountType.INVESTMENT),
    BankPreset("eToro",           clearbit("etoro.com"),            "#00C853", AccountType.INVESTMENT),
    BankPreset("Trade Republic",  clearbit("traderepublic.com"),    "#C9F135", AccountType.INVESTMENT),
)
