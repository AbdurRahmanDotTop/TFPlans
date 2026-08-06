package com.techilyfly.tfplans.di

import android.content.Context
import com.techilyfly.tfplans.data.AppDatabase
import com.techilyfly.tfplans.data.NoteDao
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

import com.techilyfly.tfplans.data.UserPreferencesRepository

interface AppContainer {
    val noteDao: NoteDao
    val firebaseAuth: FirebaseAuth
    val firestore: FirebaseFirestore
    val notesRepository: com.techilyfly.tfplans.data.NotesRepository
    val userPreferencesRepository: UserPreferencesRepository
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    override val noteDao: NoteDao by lazy {
        AppDatabase.getDatabase(context).noteDao()
    }
    override val firebaseAuth: FirebaseAuth by lazy {
        FirebaseAuth.getInstance()
    }
    override val firestore: FirebaseFirestore by lazy {
        val db = FirebaseFirestore.getInstance()
        val settings = com.google.firebase.firestore.FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(com.google.firebase.firestore.PersistentCacheSettings.newBuilder().build())
            .build()
        db.firestoreSettings = settings
        db
    }
    override val userPreferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepository(context)
    }
    override val notesRepository: com.techilyfly.tfplans.data.NotesRepository by lazy {
        com.techilyfly.tfplans.data.NotesRepository(context, noteDao, firestore, firebaseAuth, userPreferencesRepository)
    }
}
