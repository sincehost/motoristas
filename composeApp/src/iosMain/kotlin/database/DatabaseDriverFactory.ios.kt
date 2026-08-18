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
            "ALTER TABLE Abastecimento ADD COLUMN valor_litro TEXT NOT NULL DEFAULT ''",
            // Colunas da FinalizacaoViagem (retorno de carga)
            "ALTER TABLE FinalizacaoViagem ADD COLUMN teve_retorno INTEGER NOT NULL DEFAULT 0",
            "ALTER TABLE FinalizacaoViagem ADD COLUMN local_carregou TEXT",
            "ALTER TABLE FinalizacaoViagem ADD COLUMN ordem_retorno TEXT",
            "ALTER TABLE FinalizacaoViagem ADD COLUMN cte_retorno TEXT",
            // KM do equipamento (cache local pra avisar offline se o KM de início
            // for menor que o último registrado) e ultimo_erro em todas as
            // tabelas sincronizáveis (pra descartar item travado sem precisar
            // desinstalar o app).
            "ALTER TABLE Equipamento ADD COLUMN km TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE Viagem ADD COLUMN ultimo_erro TEXT",
            "ALTER TABLE Arla ADD COLUMN ultimo_erro TEXT",
            "ALTER TABLE Abastecimento ADD COLUMN ultimo_erro TEXT",
            "ALTER TABLE Manutencao ADD COLUMN ultimo_erro TEXT",
            "ALTER TABLE FinalizacaoViagem ADD COLUMN ultimo_erro TEXT",
            "ALTER TABLE Descarga ADD COLUMN ultimo_erro TEXT",
            "ALTER TABLE OutraDespesa ADD COLUMN ultimo_erro TEXT",
            "ALTER TABLE ChecklistPreViagem ADD COLUMN ultimo_erro TEXT",
            "ALTER TABLE ChecklistPosViagem ADD COLUMN ultimo_erro TEXT",
            // Manutenção Preventiva: classificação preventiva/corretiva.
            "ALTER TABLE Manutencao ADD COLUMN tipo_manutencao TEXT NOT NULL DEFAULT 'preventiva'"
        )
        for (sql in migrações) {
            try {
                driver.execute(null, sql, 0)
            } catch (_: Exception) {}
        }
        return driver
    }
}
