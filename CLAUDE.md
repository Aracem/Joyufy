# Joyufy — Contexto del proyecto para Claude

## Qué es Joyufy
App de escritorio (Mac, Windows, Linux) para control personal de finanzas.
Stack: Kotlin + Compose Multiplatform, SQLDelight, Koin.

## Decisiones de diseño tomadas

- **Sin multimoneda**: todo en EUR, una sola moneda.
- **Sin autenticación**: app de usuario único local.
- **Tipos de cuenta**: Banco, Inversión, Efectivo.
  - Banco/Efectivo: modelo de transacciones (ingreso, gasto, transferencia, depósito a inversión).
  - Inversión: modelo de snapshots semanales (se introduce el valor total manualmente cada semana).
- **Registro de balance**: semanal. La pantalla principal muestra el total de patrimonio (suma de todas las cuentas).
- **Gráfica principal**: evolución del patrimonio total + desglose por cuenta. Rango por defecto: 1 año.
  - Vista A (por defecto): línea con área rellena en gradiente (estilo TradeRepublic).
  - Vista C (alternativa): barras semanales para análisis granular.
  - Toggle en la esquina de la gráfica para cambiar entre ambas.

- **Categorías de transacción**: lista de sugerencias predefinidas (Nómina, Supermercado, Alquiler...) + texto libre. Las predefinidas son sugerencias, no obligatorias.
- **Color de cuenta**: paleta de ~10 colores temáticos predefinidos + color picker libre para cuando hay muchas cuentas o se quiere personalizar más.
- **Snapshot semanal de inversión**: opción C — aviso/banner al abrir la app si hay cuentas sin actualizar, que lleva directo al detalle de la cuenta.

## Estilo visual

- Dark-first (soporte light en el futuro)
- Referencias: Revolut, TradeRepublic, Notion
- Fondo: #0F0F0F / Superficie: #1A1A1A / Superficie2: #242424
- Acento: #7B6EF6 — Positivo: #34C77B — Negativo: #F25C5C
- Tipografía: Inter, tabular figures para cantidades

## Mejoras futuras (no implementar aún)

- **Conexión a APIs de inversión / scraping**: algunas plataformas (Indexa, DeGiro, TradeRepublic) podrían ofrecer API o scraping para automatizar los snapshots semanales de inversión. Diseñar la capa de datos de forma que sea fácil enchufar una fuente externa por cuenta.
- **Múltiples perfiles/usuarios**: la app es hoy de usuario único, pero podría necesitar soporte multi-perfil (distintas personas en el mismo ordenador, o perfiles separados por contexto). Tenerlo en cuenta en el modelo de datos si se refactoriza la capa de persistencia.
- **Backup / sync en la nube**: posibilidad de conectar un servicio externo para guardar copia de seguridad de los datos locales.
- **Importación de extractos bancarios**: CSV/OFX para cuentas de banco, además de introducción manual.
