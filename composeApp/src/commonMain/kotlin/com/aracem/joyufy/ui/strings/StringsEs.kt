package com.aracem.joyufy.ui.strings

val StringsEs = Strings(
    // Navigation
    goBack = "Volver",
    sidebarDashboard = "Dashboard",
    sidebarSettings = "Ajustes",
    sidebarCollapse = "Colapsar sidebar",
    sidebarExpand = "Expandir sidebar",
    sidebarReorderEnter = "Ordenar cuentas",
    sidebarReorderExit = "Salir del modo ordenar",
    sidebarReorder = "Reordenar",
    sidebarNewAccount = "Nueva cuenta",
    sidebarDarkMode = "Modo claro",
    sidebarLightMode = "Modo oscuro",

    // Common actions
    cancel = "Cancelar",
    delete = "Eliminar",
    edit = "Editar",
    saveChanges = "Guardar cambios",
    close = "Cerrar",
    download = "Descargar",

    // Dashboard
    totalWealth = "Patrimonio total",
    noAccountsYet = "Sin cuentas todavía",
    addFirstAccount = "Añade tu primera cuenta desde el panel izquierdo",
    evolution = "Evolución",
    changeView = "Cambiar vista",
    analysis = "Análisis",
    collapse = "Colapsar",
    expand = "Expandir",

    // Monthly summary
    currentMonth = "Este mes",
    income = "Ingresos",
    expenses = "Gastos",
    investment = "Inversión",
    net = "Neto",
    topExpenses = "Top gastos",
    monthCurrent = "Mes actual",

    // Annual summary
    currentYear = "Este año",
    yearAll = "Todo",
    yearPrevious = "Año anterior",
    yearNext = "Año siguiente",

    // Chart ranges
    chartWeek = "1S",
    chartMonth = "1M",
    chartThreeMonths = "3M",
    chartSixMonths = "6M",
    chartYtd = "YTD",
    chartYear = "1A",
    rangeOneWeek = "en la última semana",
    rangeOneMonth = "en el último mes",
    rangeThreeMonths = "en los últimos 3 meses",
    rangeSixMonths = "en los últimos 6 meses",
    rangeYtd = "en lo que va de año",
    rangeOneYear = "en el último año",
    rangeAll = "desde el inicio",

    // Account detail
    editAccount = "Editar cuenta",
    updateValue = "Actualizar valor",
    addTransaction = "Añadir transacción",
    currentBalance = "Balance actual",
    transactions = "Transacciones",
    noTransactions = "Aún no hay transacciones",
    noTransactionsHint = "Pulsa «Añadir transacción» para registrar la primera",
    noSearchResults = "Sin resultados",
    noSearchResultsHint = "Prueba a cambiar o limpiar los filtros",
    weeklyValue = "Valor de mercado semanal",
    noWeeklyRecords = "Sin registros semanales",
    noWeeklyRecordsHint = "Pulsa «Actualizar valor» para añadir el primero",
    editValue = "Editar valor",
    weekCurrent = "Semana actual — ",
    week = "Semana",

    // Filters
    clearFilters = "Limpiar",
    searchDescriptionCategory = "Buscar por descripción o categoría…",
    transactionType = "Tipo",
    transferOut = "Transferencia →",
    transferIn = "Transferencia ←",

    // Add/Edit transaction
    editTransaction = "Editar transacción",
    newTransaction = "Nueva transacción",
    amountEur = "Importe (€)",
    dateFormat = "Fecha (dd/MM/aaaa)",
    categoryOptional = "Categoría (opcional)",
    descriptionOptional = "Descripción (opcional)",
    destinationAccount = "Cuenta destino",
    placeholderAmount = "0,00",
    buttonTransfer = "Transferir",
    buttonAdd = "Añadir",
    amountError = "Introduce un importe válido",
    dateError = "Usa el formato dd/MM/aaaa",

    // Add/Edit snapshot
    totalValueEur = "Valor total (€)",
    valueError = "Introduce un valor válido",
    save = "Guardar",

    // Create account
    newAccount = "Nueva cuenta",
    createAccount = "Crear cuenta",
    name = "Nombre",
    typeLabel = "Tipo",
    color = "Color",
    placeholderName = "Ej: Banco Santander",
    customColor = "Color personalizado",
    placeholderColor = "#7B6EF6",
    initialBalanceOptional = "Saldo inicial (opcional)",
    bankOrPlatform = "Banco o plataforma",
    moreOptions = "Más opciones",

    // Account types
    accountTypeBank = "Banco",
    accountTypeInvestment = "Inversión",
    accountTypeCash = "Efectivo",

    // Transaction types
    transactionIncome = "Ingreso",
    transactionExpense = "Gasto",
    transactionTransfer = "Transferencia",

    // Settings
    settings = "Ajustes",
    appearance = "Apariencia",
    darkMode = "Modo oscuro",
    lightMode = "Modo claro",
    themeDescription = "Cambia entre tema oscuro y claro",
    data = "Datos",
    exportBackup = "Exportar backup",
    importBackup = "Importar backup",
    exportData = "Exportar datos",
    importData = "Importar datos",
    accounts = "Cuentas",
    noAccounts = "Sin cuentas",
    dangerZone = "Zona de peligro",
    deleteAllData = "Borrar todos los datos",
    language = "Idioma",

    // Cloud sync
    cloudSync = "Sincronización en la nube",
    connectDrive = "Conectar Google Drive",
    disconnectDrive = "Desconectar",
    driveConnected = "Conectado como %s",
    uploadNow = "Subir ahora",
    restoreFromDrive = "Restaurar desde Drive",
    autoSync = "Sync automático",
    autoSyncDescription = "Sube al cerrar, descarga al abrir",
    lastSync = "Última sync: %s",
    syncSuccess = "Sincronizado con Google Drive",
    syncError = "Error al sincronizar",
    syncing = "Sincronizando…",
    confirmRestoreFromDrive = "¿Restaurar desde Drive?",
    confirmRestoreFromDriveText = "Todos los datos actuales se reemplazarán con el backup de Drive. Esta acción no se puede deshacer.",

    // Confirm dialogs
    confirmDeleteTransaction = "¿Eliminar transacción?",
    confirmDeleteTransactionText = "Esta acción no se puede deshacer.",
    confirmDeleteSnapshot = "¿Eliminar registro semanal?",
    confirmDeleteSnapshotText = "Esta acción no se puede deshacer.",
    confirmDeleteAccount = "¿Eliminar cuenta?",
    confirmDeleteAccountText = "Se eliminarán permanentemente la cuenta y todas sus transacciones y snapshots. Esta acción no se puede deshacer.",
    confirmDeleteAll = "¿Borrar todos los datos?",
    confirmDeleteAllText = "Se eliminarán todas las cuentas, transacciones y snapshots. La aplicación quedará vacía. Esta acción no se puede deshacer.",
    deleteAll = "Borrar todo",
    confirmRestoreBackup = "¿Restaurar backup?",
    confirmRestoreBackupText = "Se borrarán todos los datos actuales y se reemplazarán con los del archivo. Esta acción no se puede deshacer.",
    restore = "Restaurar",
)
