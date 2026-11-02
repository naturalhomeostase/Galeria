package com.galeria.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.galeria.data.model.AlbumEntity
import com.galeria.data.model.AlbumPhotoCrossRef
import com.galeria.data.model.FavoriteEntity
import com.galeria.data.model.HiddenFolderEntity
import com.galeria.data.model.SafFolderEntity
import com.galeria.data.model.SecurityEntity

@Database(
    entities = [
        AlbumEntity::class,
        AlbumPhotoCrossRef::class,
        FavoriteEntity::class,
        SecurityEntity::class,
        HiddenFolderEntity::class,
        SafFolderEntity::class
    ],
    version = 5,
    // Com exportSchema = true o Room grava um JSON do esquema a cada versão em
    // app/schemas/ (ver ksp { arg("room.schemaLocation", ...) } no build.gradle.kts).
    // Versione esses arquivos no git: é a base para escrever e testar as próximas migrações.
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun albumDao(): AlbumDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun securityDao(): SecurityDao
    abstract fun hiddenFolderDao(): HiddenFolderDao
    abstract fun safFolderDao(): SafFolderDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        // v4 -> v5: endurecimento da senha do cofre.
        //  - iterations: 0 mantém o hash antigo (SHA-256) válido até o próximo login, quando ele
        //    é regravado como PBKDF2.
        //  - failedAttempts / lockedUntil: limite de tentativas persistente.
        // Os DEFAULTs aqui precisam bater com @ColumnInfo(defaultValue) em SecurityEntity, senão
        // o Room recusa o esquema ao abrir o banco.
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE security ADD COLUMN iterations INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE security ADD COLUMN failedAttempts INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE security ADD COLUMN lockedUntil INTEGER NOT NULL DEFAULT 0")
            }
        }

        // REGRA DAQUI PRA FRENTE: toda vez que mudar uma @Entity, suba `version` e adicione aqui
        // uma Migration(N, N+1). Sem ela o app falha ao abrir o banco (de propósito: é melhor
        // do que apagar favoritos, álbuns e a senha do cofre em silêncio, como acontecia com
        // fallbackToDestructiveMigration).
        private val ALL_MIGRATIONS = arrayOf(MIGRATION_4_5)

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "galeria.db"
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    // Versões 1 a 3 são de builds antigas cujo esquema não foi versionado, então
                    // não há como escrever uma migração confiável a partir delas. Só elas ainda
                    // recriam o banco do zero; qualquer outro salto sem migração falha.
                    .fallbackToDestructiveMigrationFrom(1, 2, 3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
