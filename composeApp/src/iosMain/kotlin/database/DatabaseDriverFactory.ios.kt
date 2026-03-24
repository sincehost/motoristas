package database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import br.com.lfsystem.app.database.AppDatabase

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        val driver = NativeSqliteDriver(AppDatabase.Schema, "lfsystem.db")
        // Migração segura: adicionar colunas que podem não existir em bancos antigos
        val migrações = listOf(
            // Colunas do Abastecimento
            "ALTER TABLE Abastecimento ADD COLUMN tipo_combustivel TEXT NOT NULL DEFAULT 'Diesel Caminhão'",
            "ALTER TABLE Abastecimento ADD COLUMN horas TEXT",
            // Colunas da FinalizacaoViagem (retorno de carga)
            "ALTER TABLE FinalizacaoViagem ADD COLUMN teve_retorno INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE FinalizacaoViagem ADD COLUMN local_carregou TEXT",
            "ALTER TABLE FinalizacaoViagem ADD COLUMN ordem_retorno TEXT",
            "ALTER TABLE FinalizacaoViagem ADD COLUMN cte_retorno TEXT"
        )
        for (sql in migrações) {
            try {
                driver.execute(null, sql, 0)
            } catch (_: Exception) {}
        }
        return driver
    }
}
