package com.aracem.joyufy.domain.model

data class BankPreset(
    val name: String,
    val logoRes: String?,       // drawable resource name without extension, e.g. "logo_santander"
    val defaultColor: String,
    val type: AccountType,
)

val BankPresets: List<BankPreset> = listOf(
    // ── Bancos ────────────────────────────────────────────────────────────────
    BankPreset("Santander",   "logo_santander",   "#EC0000", AccountType.BANK),
    BankPreset("BBVA",        "logo_bbva",        "#004481", AccountType.BANK),
    BankPreset("CaixaBank",   "logo_caixabank",   "#0066CC", AccountType.BANK),
    BankPreset("Bankinter",   "logo_bankinter",   "#FF6600", AccountType.BANK),
    BankPreset("Sabadell",    "logo_sabadell",    "#006BA6", AccountType.BANK),
    BankPreset("ING",         "logo_ing",         "#FF6200", AccountType.BANK),
    BankPreset("Unicaja",     "logo_unicaja",     "#006633", AccountType.BANK),
    BankPreset("Kutxabank",   "logo_kutxabank",   "#E30613", AccountType.BANK),
    BankPreset("Abanca",      "logo_abanca",      "#00A651", AccountType.BANK),
    BankPreset("Openbank",    "logo_openbank",    "#FF0000", AccountType.BANK),
    BankPreset("N26",         "logo_n26",         "#00C6A7", AccountType.BANK),
    BankPreset("Revolut",     "logo_revolut",     "#191C1F", AccountType.BANK),
    // ── Inversión ─────────────────────────────────────────────────────────────
    BankPreset("Indexa Capital",  null,                  "#1A1A2E", AccountType.INVESTMENT),
    BankPreset("MyInvestor",      "logo_myinvestor",     "#FF4F00", AccountType.INVESTMENT),
    BankPreset("inbestMe",        null,                  "#00C389", AccountType.INVESTMENT),
    BankPreset("Finizens",        null,                  "#1565C0", AccountType.INVESTMENT),
    BankPreset("Renta 4",         null,                  "#E4032E", AccountType.INVESTMENT),
    BankPreset("Self Bank",       null,                  "#005B99", AccountType.INVESTMENT),
    BankPreset("DEGIRO",          "logo_degiro",         "#FF6600", AccountType.INVESTMENT),
    BankPreset("eToro",           "logo_etoro",          "#00C853", AccountType.INVESTMENT),
    BankPreset("Trade Republic",  "logo_traderepublic",  "#C9F135", AccountType.INVESTMENT),
)
