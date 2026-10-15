package com.lielu.keep.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [FolderEntity::class, LinkEntity::class, LinkTagEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun linksDao(): LinksDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room
                .databaseBuilder(context.applicationContext, AppDatabase::class.java, "lielukeep.db")
                .addMigrations(*ALL_MIGRATIONS)
                .build()
    }
}
