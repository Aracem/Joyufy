# Joyufy

**Control personal de finanzas para escritorio.**
Diseñado para quien quiere ver su patrimonio total de un vistazo, sin bancos conectados, sin suscripciones, sin datos en la nube.

[![Release](https://img.shields.io/github/v/release/Aracem/Joyufy?style=flat-square&color=7B6EF6&label=última%20versión)](https://github.com/Aracem/Joyufy/releases/latest)
[![Platform](https://img.shields.io/badge/plataforma-macOS%20·%20Windows%20·%20Linux-lightgrey?style=flat-square)](https://github.com/Aracem/Joyufy/releases/latest)
[![License](https://img.shields.io/github/license/Aracem/Joyufy?style=flat-square)](LICENSE)

---

## Descarga

→ **[Descarga la última versión](https://github.com/Aracem/Joyufy/releases/latest)**

| Sistema | Formato |
|---|---|
| macOS | `.dmg` |
| Windows | `.msi` |
| Linux | `.deb` |

---

## Qué hace

- **Patrimonio total** — suma de todas tus cuentas en tiempo real con gráfica de evolución
- **Cuentas bancarias y efectivo** — registro de transacciones con categorías
- **Inversiones** — seguimiento semanal de valor de mercado (Indexa, DeGiro, Trade Republic…)
- **Resumen mensual** — ingresos, gastos y top categorías de gasto del mes actual
- **Backup local** — exporta e importa todos tus datos en JSON

---

## Instalación en macOS

Al abrir la app por primera vez macOS mostrará un aviso de seguridad porque la app no está firmada con un certificado de Apple. Para abrirla:

1. Click derecho sobre `Joyufy.app` → **Abrir**
2. En el diálogo, click **Abrir igualmente**

O desde Terminal:
```bash
xattr -cr /Applications/Joyufy.app
```

Solo es necesario hacerlo una vez.

---

## Stack

Kotlin · Compose Multiplatform · SQLDelight · Koin

Aplicación de escritorio nativa (macOS / Windows / Linux). Todos los datos se guardan localmente.

---

## Desarrollo

```bash
./gradlew run                 # ejecutar en desarrollo
./gradlew packageDmg          # generar instalador macOS
./gradlew packageMsi          # generar instalador Windows
./gradlew packageDeb          # generar instalador Linux
```
