package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [MessageEntity::class, NodeEntity::class], version = 1, exportSchema = false)
abstract class MeshDatabase : RoomDatabase() {
    abstract fun meshDao(): MeshDao

    companion object {
        @Volatile
        private var INSTANCE: MeshDatabase? = null

        fun getDatabase(context: Context): MeshDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    MeshDatabase::class.java,
                    "offgrid_mesh_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
