package database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import br.com.lfsystem.app.database.AppDatabase

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver {
        return AndroidSqliteDriver(
            schema = AppDatabase.Schema,
            context = context,
            name = "lfsystem.db",
            callback = object : AndroidSqliteDriver.Callback(AppDatabase.Schema) {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    // Garantir que colunas novas existam (migração segura)
                    // Se a coluna já existe, o ALTER TABLE falha silenciosamente no try/catch
                    try {
                        db.execSQL("ALTER TABLE FinalizacaoViagem ADD COLUMN teve_retorno INTEGER NOT NULL DEFAULT 0")
                    } catch (_: Exception) {}
                    try {
                        db.execSQL("ALTER TABLE FinalizacaoViagem ADD COLUMN local_carregou TEXT")
                    } catch (_: Exception) {}
                    try {
                        db.execSQL("ALTER TABLE FinalizacaoViagem ADD COLUMN ordem_retorno TEXT")
                    } catch (_: Exception) {}
                    try {
                        db.execSQL("ALTER TABLE FinalizacaoViagem ADD COLUMN cte_retorno TEXT")
                    } catch (_: Exception) {}
                    // Migração: tipo_combustivel e horas no Abastecimento
                    try {
                        db.execSQL("ALTER TABLE Abastecimento ADD COLUMN tipo_combustivel TEXT NOT NULL DEFAULT 'Diesel Caminhão'")
                    } catch (_: Exception) {}
                    try {
                        db.execSQL("ALTER TABLE Abastecimento ADD COLUMN horas TEXT")
                    } catch (_: Exception) {}
                    try {
                        db.execSQL("ALTER TABLE Abastecimento ADD COLUMN valor_litro TEXT NOT NULL DEFAULT ''")
                    } catch (_: Exception) {}
                    // Migração: KM do equipamento (cache local pra avisar offline se o
                    // KM de início for menor que o último registrado) e ultimo_erro em
                    // todas as tabelas sincronizáveis (pra descartar item travado sem
                    // precisar desinstalar o app).
                    try {
                        db.execSQL("ALTER TABLE Equipamento ADD COLUMN km TEXT NOT NULL DEFAULT ''")
                    } catch (_: Exception) {}
                    for (tabela in listOf(
                        "Viagem", "Arla", "Abastecimento", "Manutencao", "FinalizacaoViagem",
                        "Descarga", "OutraDespesa", "ChecklistPreViagem", "ChecklistPosViagem"
                    )) {
                        try {
                            db.execSQL("ALTER TABLE $tabela ADD COLUMN ultimo_erro TEXT")
                        } catch (_: Exception) {}
                    }
                    // Manutenção Preventiva: classificação preventiva/corretiva.
                    try {
                        db.execSQL("ALTER TABLE Manutencao ADD COLUMN tipo_manutencao TEXT NOT NULL DEFAULT 'preventiva'")
                    } catch (_: Exception) {}
                }
            }
        )
    }
}
