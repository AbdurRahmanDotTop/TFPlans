# TFPlans Application Documentation

## 1. Project Overview & Architecture
**TFPlans** is a robust Android application designed for note-taking, checklist management, and task organization. It features a rich text block editor, local and cloud sync capabilities, AI integration, and reminder scheduling.

**Architecture:**
The application follows the **Model-View-ViewModel (MVVM)** architecture pattern combined with **Clean Architecture** principles.
- **UI Layer (View):** Built entirely with **Jetpack Compose**. Handles rendering and user interactions.
- **Presentation Layer (ViewModel):** Manages UI state, handles user events, and communicates with repositories.
- **Data Layer (Repository):** `NotesRepository` and `UserPreferencesRepository` act as the single source of truth, abstracting data sources (Room database, Firebase Firestore, SharedPreferences).
- **Persistence Layer:** Local storage via **Room Database** and remote backup via **Firebase Firestore**.

**Technology Stack:**
- **Language:** Kotlin
- **UI Toolkit:** Jetpack Compose
- **Local Database:** Room Database
- **Remote Database & Auth:** Firebase (Firestore, Auth, AppCheck)
- **Dependency Injection:** Manual DI via `AppContainer` (`TFPlansApplication`)
- **Background Tasks:** WorkManager (`SyncWorker`)
- **Image Loading:** Coil
- **AI Integration:** Google Generative AI (Gemini)
- **Monetization:** Google AdMob (UMP for consent)

---

## 2. Folder & File Structure
```text
app/src/main/java/com/techilyfly/tfplans/
├── ai/                 # AI integrations (GenerativeAiProvider)
├── data/               # Data Layer (Room DB Entities, DAOs, Repositories, Workers)
│   ├── AppDatabase.kt
│   ├── Note.kt
│   ├── NoteDao.kt
│   ├── NotesRepository.kt
│   ├── SyncWorker.kt
│   └── UserPreferencesRepository.kt
├── di/                 # Dependency Injection Container (AppContainer)
├── reminders/          # AlarmManager and Notification scheduling
│   ├── AlarmReceiver.kt
│   ├── BootReceiver.kt
│   ├── NotificationReceiver.kt
│   └── ReminderScheduler.kt
├── ui/                 # UI Layer (Compose Screens and ViewModels)
│   ├── auth/           # Authentication (Login/Signup)
│   ├── components/     # Reusable UI components (Bottom bar, Ads)
│   ├── edit/           # Note Editing (Rich text BlockEditor, Color picker)
│   ├── home/           # Dashboard (Notes grid/list)
│   ├── profile/        # User Profile Management
│   ├── settings/       # App Settings & Preferences
│   └── theme/          # Compose Theme (Colors, Typography)
├── util/               # Utility classes (NetworkConnectivityObserver)
├── MainActivity.kt     # Single Activity entry point & Navigation Graph
└── TFPlansApplication.kt # Custom Application class for initialization
```

---

## 3. Database Schema and Relationships (Room)

### **Table: `notes`**
The primary entity for storing notes and tasks.

| Field | Type | Description |
|---|---|---|
| `id` | String | Primary Key (UUID) |
| `title` | String | Title of the note |
| `content` | String | Serialized block content (JSON or text) |
| `color` | Int | Note background color (ARGB) |
| `category` | String | Category tag |
| `reminderTime` | Long | Epoch timestamp for reminders |
| `reminderRepeat` | String | Repeat interval (`NONE`, `DAILY`, `WEEKLY`, `MONTHLY`, `YEARLY`) |
| `isDone` | Boolean | Task completion status |
| `isPinned` | Boolean | Whether the note is pinned to the top |
| `isArchived` | Boolean | Soft delete / Archived state |
| `isDeleted` | Boolean | Soft delete state (Moved to trash) |
| `isSynced` | Boolean | Tracks if changes are synced to Firestore |
| `createdAt` | Long | Creation timestamp |
| `updatedAt` | Long | Last modification timestamp |

---

## 4. Authentication & Authorization Flow

**Authentication Providers:**
- **Google Sign-In:** Utilizes Android `CredentialManager` and `GoogleIdTokenCredential`.
- **Firebase Authentication:** Handles the backend user session and token verification.

**User Flow:**
1. User opens the app. `MainActivity` checks `FirebaseAuth.getInstance().currentUser`.
2. If `null`, routes to `AuthScreen`. If authenticated, routes to `HomeScreen`.
3. In `AuthScreen`, user clicks "Continue with Google".
4. `CredentialManager` prompts the Google Account picker.
5. On success, the ID Token is passed to `Firebase Auth` for sign-in.
6. Upon successful Firebase authentication, the user is navigated to `HomeScreen`.

**Permissions & Roles:**
- There are no admin roles. Every authenticated user has isolated read/write access to their own data in Firestore via Security Rules (`/Users/{userId}/*`).

---

## 5. Features & Modules

### 5.1. Authentication Module
- **Purpose:** Securely identify users and provide cloud synchronization capabilities.
- **UI Components:** `AuthScreen.kt`
- **Business Logic:** `AuthViewModel.kt` handles the credential retrieval and Firebase sign-in.
- **Error Handling:** Catches `GetCredentialException` (e.g., user canceled) and Firebase Auth exceptions, displaying a `Snackbar`.

### 5.2. Home Dashboard (Notes List)
- **Purpose:** Display all active, pinned, and category-filtered notes.
- **UI Components:** `HomeScreen.kt`, `NativeAdCard.kt`
- **How it Works:** Uses a `LazyVerticalStaggeredGrid` to display notes. Observes `NotesRepository.getActiveNotes()` via `HomeViewModel`.
- **User Flow:** User views notes, taps to edit, long-presses/swipes for quick actions (Archive/Delete). Search bar filters notes dynamically.
- **Inputs & Outputs:** Input: Search query, category filter. Output: Filtered list of `Note` objects.

### 5.3. Note Editing & Rich Text (`BlockEditor`)
- **Purpose:** Provide a robust, block-based text editor supporting rich text, checklists, images, and audio.
- **UI Components:** `EditNoteScreen.kt`, `BlockEditor.kt`, `AdvancedColorPicker.kt`
- **How it Works:**
  - `BlockEditor` parses raw string content into `NoteBlock` sealed classes (`TextBlock`, `ChecklistBlock`, `ImageBlock`, `AudioBlock`) using JSON serialization.
  - Supports applying `SpanStyle` (Text Color, Highlight Color, Bold, Italic) across line breaks using a custom `getDiffAnnotatedString` diffing algorithm.
- **AI Integration:** Features a "Magic Edit" (Gemini API) that suggests improvements, summaries, or expansions of the note content.
- **Edge Cases Handling:** Empty blocks are automatically purged or managed. Format styles carry over line breaks correctly.

### 5.4. Settings & Preferences
- **Purpose:** User customization and data management.
- **UI Components:** `SettingsScreen.kt`, `InAppBrowserScreen.kt` (for privacy policy/terms).
- **Business Logic:** Managed by `UserPreferencesRepository` using `SharedPreferences`.
- **Settings Available:** Theme Mode (Light/Dark/System), Font Size, Default View (Grid/List), Cloud Backup Toggle.

### 5.5. Reminder System
- **Purpose:** Notify users at a specific date and time regarding a note.
- **Dependencies:** `AlarmManager`, `NotificationManager`, `BroadcastReceiver`.
- **How it Works:** 
  - User sets a `reminderTime`. `NotesRepository.saveNote()` calls `ReminderScheduler.scheduleReminder()`.
  - An exact alarm is scheduled via `AlarmManager`.
  - `AlarmReceiver` wakes up, triggers `NotificationReceiver` which posts an Android System Notification.
- **Permissions Required:** `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `USE_EXACT_ALARM`.
- **Edge Cases:** `BootReceiver` reschedules all active alarms when the device is rebooted.

---

## 6. Third-Party Integrations
1. **Firebase (Firestore & Auth):** Remote database for syncing notes across devices. Handled in `NotesRepository`.
2. **Google AdMob:** Monetization via banner and native ads (`AdManager.kt`, `NativeAdCard.kt`). Uses UMP for GDPR/CCPA consent.
3. **Google Generative AI (Gemini):** Used in `GenerativeAiProvider.kt` for text generation capabilities within the note editor.
4. **Coil:** Image loading and caching for attached images.

---

## 7. Background Sync & Notifications (WorkManager)
- **SyncWorker:** A periodic `WorkManager` job running every 15 minutes (if connected to network).
- **Realtime Sync:** When the app is open, `NotesRepository` attaches a Firestore `addSnapshotListener` to merge remote changes immediately.
- **Conflict Resolution:** Last-write-wins based on the `updatedAt` timestamp. Local unsynced changes override older remote changes.

---

## 8. Security Features
- **Local Data:** Stored in app-private storage (Room DB).
- **AppCheck Integration:** Firebase AppCheck with reCAPTCHA is configured to prevent unauthorized API calls to backend services.
- **Firestore Rules:** Data is strictly sandboxed to the authenticated user's UID.
- **Secrets Management:** Sensitive keys (`GEMINI_API_KEY`, `RECAPTCHA_SITE_KEY`) are injected at compile-time via `secrets-gradle-plugin` and `.env` file, keeping them out of the source code.

---

## 9. Performance Optimizations
- **Coroutines & Flows:** Extensive use of Kotlin Coroutines for asynchronous operations and StateFlow for reactive UI updates, preventing UI thread blocking.
- **Local Caching:** Room acts as the Single Source of Truth (SSOT). Network requests only happen in the background (`SyncWorker`), allowing full offline capability.
- **Staggered Grid Lazy Loading:** Notes are lazy-rendered in `HomeScreen`, minimizing memory footprint for users with thousands of notes.

---

## 10. Future Improvement Suggestions
1. **End-to-End Encryption (E2EE):** Encrypt note `content` and `title` locally using the Android Keystore before syncing to Firestore, ensuring zero-knowledge privacy.
2. **Conflict Merging Algorithm:** Currently uses "Last-write-wins". Implementing Operational Transformation (OT) or CRDTs would allow seamless collaborative editing.
3. **Media Cloud Storage:** Currently, image/audio URIs are local. Integrate Firebase Storage to sync attached media files.
4. **Pagination:** Implement Room/Firestore pagination if the note count exceeds 10,000 for faster initial load times.

---

*This document was generated automatically and serves as the single source of truth for the TFPlans application architecture and capabilities.*
