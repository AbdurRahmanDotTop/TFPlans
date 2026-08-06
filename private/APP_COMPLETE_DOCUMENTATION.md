# TF Plans - Comprehensive Application Documentation

Welcome to the comprehensive documentation for **TF Plans**. This document is designed to provide a deep understanding of the entire application, including its features, workflows, integrations, and capabilities. It is structured to be accessible for both non-technical users and developers.

---

## Section 1: User Features (A–Z)

### 1. AI Assistant (Generative AI)
* **Feature Name:** Generative AI Assistant
* **Purpose:** Helps users enhance, rewrite, or summarize their notes using artificial intelligence (Google Gemini).
* **Detailed Description:** When editing a note, users can invoke the AI Assistant to process their note's content. Available actions typically include summarizing long text, fixing grammar, elaborating on bullet points, or changing the tone. The AI processes the request and provides a preview.
* **How the user accesses it:** In the "Note Editing" screen, click the magic wand (`AutoAwesome`) icon in the top right corner to open the AI Bottom Sheet.
* **How it works internally (user perspective):** The app sends the current note content and the selected action to the Google Gemini API. It shows a loading indicator, then presents a preview dialog. If the user clicks "Accept & Apply," the new content is injected into the note.
* **Benefits:** Saves time, improves writing quality, and helps overcome writer's block.
* **Real-world use case:** A user quickly jots down messy meeting notes and uses the AI to "Summarize" or "Fix Grammar" before sharing them.
* **Available Settings/Configurations:** None directly exposed to the user (requires internet connection).

### 2. Audio Recordings
* **Feature Name:** Audio Notes
* **Purpose:** Allows users to record and embed voice memos directly into their text notes.
* **Detailed Description:** Users can attach an audio recording block within a note. The app requests microphone permissions, records the audio to local storage, and inserts a playable audio block inline with the text.
* **How the user accesses it:** In the "Note Editing" screen, click the Microphone icon in the bottom formatting toolbar.
* **How it works internally:** Uses Android's `MediaRecorder` to save an audio file. An `AudioBlock` containing the local URI is appended to the `BlockEditor`'s JSON state.
* **Benefits:** Hands-free note-taking; capturing lectures or thoughts on the go.
* **Real-world use case:** Recording a quick voice thought while walking, instead of typing it out.

### 3. Authentication & Account Management
* **Feature Name:** User Authentication
* **Purpose:** Secures user data and links it to a personal account for cloud synchronization.
* **Detailed Description:** Supports signing in via Google or standard Email/Password. Users can also reset their password, verify their email, edit their profile (Display Name, Photo URL), and permanently delete their account.
* **How the user accesses it:** "Sign In" is the initial screen for unauthenticated users. Account management is located in the `ProfileScreen` (accessed via the user avatar in the Home/Settings header).
* **How it works internally:** Integrated with Firebase Authentication. Account deletions wipe both Firebase Auth records and associated Firestore cloud data. Re-authentication is enforced for sensitive actions (e.g., password changes or account deletion).
* **Benefits:** Cross-device access, data security, and seamless Google integration.
* **Real-world use case:** A user gets a new phone and signs in with their Google account to instantly retrieve all their notes.

### 4. Checklists & Formatting
* **Feature Name:** Rich Text & Block Editor
* **Purpose:** Provides powerful formatting tools to structure notes effectively.
* **Detailed Description:** The editor supports multiple block types. Users can convert text blocks to checklists, apply text colors, highlight colors, and use standard text spans (bold, italic, strikethrough, underline).
* **How the user accesses it:** In the "Note Editing" screen, via the formatting toolbar above the keyboard.
* **How it works internally:** Uses a custom `BlockEditor` that maintains a list of `NoteBlock` objects (Text, Checklist, Image, Audio). Text formatting is achieved using Jetpack Compose's `AnnotatedString` and custom span serialization to JSON.
* **Benefits:** Highly organized, visually distinct notes.
* **Real-world use case:** Creating a grocery list (using checklists) and highlighting urgent items in red.

### 5. Cloud Synchronization
* **Feature Name:** Firebase Cloud Sync
* **Purpose:** Backs up local notes to the cloud and keeps them synchronized across devices.
* **Detailed Description:** The app uses local-first architecture (Room Database) but synchronizes changes to Firebase Firestore in the background. Users can trigger a manual sync or view their "Last Cloud Sync" timestamp.
* **How the user accesses it:** Automatically happens in the background. Manual sync is found in the `ProfileScreen` under "Sync Now".
* **How it works internally:** A `SyncWorker` (WorkManager) handles background synchronization to Firestore. It resolves conflicts based on the `updatedAt` timestamp.
* **Benefits:** Data loss prevention.
* **Real-world use case:** Recovering notes after a device is lost or broken.

### 6. Image Attachments
* **Feature Name:** Image Blocks
* **Purpose:** Allows users to insert visual content into notes.
* **Detailed Description:** Users can pick images from their device gallery and embed them inline with text.
* **How the user accesses it:** In the "Note Editing" screen, click the Image icon in the bottom formatting toolbar.
* **How it works internally:** The selected image URI is copied to app-internal storage (to guarantee persistent read permissions) and an `ImageBlock` is appended to the note content.
* **Benefits:** Enhances notes with visual context.
* **Real-world use case:** Snapping a picture of a whiteboard and adding it to a meeting note.

### 7. Note Organization (Categories, Pins, Search, Archive)
* **Feature Name:** Note Management
* **Purpose:** Helps users find and organize their notes efficiently.
* **Detailed Description:** 
  * **Categories/Tags:** Users can assign a specific category or use inline `#tags`.
  * **Pinning:** Important notes can be pinned to always appear at the top of the Home Screen.
  * **Archive:** Notes can be hidden from the main view by archiving them.
  * **Search:** A real-time search bar filters notes by title, content, and tags.
* **How the user accesses it:** 
  * Pin/Archive: Usually via long-press or note details screen.
  * Categories: Click "+ Tag" above the title in the editor.
  * Search: Magnifying glass icon on the Home Screen.
* **How it works internally:** Room database queries filter notes based on `isPinned`, `isArchived`, and `category`/content matching.
* **Benefits:** Prevents clutter as the number of notes grows.

### 8. Reminders & Alarms
* **Feature Name:** Time-based Reminders
* **Purpose:** Alerts the user about a specific note at a chosen time.
* **Detailed Description:** Users can set a specific date and time for a note. The app will trigger a system notification and alarm sound when the time is reached.
* **How the user accesses it:** In the "Note Editing" screen, click "+ Set Reminder" above the title.
* **How it works internally:** Uses Android's `AlarmManager` and a `BroadcastReceiver` (`AlarmReceiver.kt`) to trigger the alert. 
* **Benefits:** Never forget important tasks or time-sensitive information.
* **Real-world use case:** Setting a reminder on a note containing a flight booking reference 2 hours before departure.

### 9. User Interface Customization (Settings)
* **Feature Name:** Appearance & View Settings
* **Purpose:** Tailors the app's look and feel to the user's preference.
* **Detailed Description:** 
  * **Theme:** Light, Dark, or System default.
  * **Font Size:** Adjustable slider (with live preview) to increase/decrease text size across the app.
  * **Default View:** Toggle between Grid View (2 columns) and List View (1 column) for the Home Screen.
* **How the user accesses it:** In the `SettingsScreen` (via the Navigation Drawer or Settings tab).
* **How it works internally:** Preferences are saved using `DataStore` or `SharedPreferences` and observed as reactive StateFlows to dynamically recompose the UI.
* **Benefits:** Accessibility (larger fonts), comfort (dark mode), and personalized layout.

---

## Section 2: Developer & Technical Details

### Architecture & Tech Stack
* **Framework:** Android Jetpack Compose (100% Kotlin)
* **Architecture Pattern:** MVVM (Model-View-ViewModel) with Unidirectional Data Flow.
* **Local Database:** Room Persistence Library (SQLite).
* **Cloud Database:** Firebase Firestore.
* **Authentication:** Firebase Auth (Google & Email/Password).
* **Background Work:** WorkManager (`SyncWorker`) for syncing; `AlarmManager` for exact time reminders.

### Key Components
1. **`BlockEditor.kt`:** The core engine for rich text editing. It serializes and parses a custom JSON format representing a mix of `TextBlock`, `ChecklistBlock`, `ImageBlock`, and `AudioBlock`. It leverages Jetpack Compose's `AnnotatedString` to handle spans (color, bold, etc.) natively.
2. **`Note.kt`:** The primary data model containing fields like `id`, `title`, `content` (JSON string), `color`, `category`, `reminderTime`, `isPinned`, `isArchived`, `createdAt`, `updatedAt`, and `syncStatus`.
3. **`AlarmReceiver.kt`:** Receives the broadcast from `AlarmManager` to post the notification and trigger any audible alarms.

### Security & Privacy
* Notes are stored locally on the device and only transmitted to Firebase if the user is authenticated.
* The app requests minimal permissions: Microphone (only when recording audio) and Notifications (for reminders, Android 13+).
* No third-party data tracking is implemented beyond Firebase Analytics/Crashlytics (if configured) and AdMob.

---

*Documentation generated automatically by AI.*
