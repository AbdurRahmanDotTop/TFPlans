# PROJECT_DOCUMENTATION

## 1. Project Overview
**Project Name:** TFPlans (Techily Fly Plans)  
**Purpose of the Application:** TFPlans is a modern, feature-rich note-taking and productivity application that leverages Artificial Intelligence to help users write, organize, and manage their ideas, reminders, and daily plans effectively.  
**Target Users:** Students, professionals, writers, and anyone looking for a smart, offline-capable note-taking app with cloud sync and AI assistance.  
**Main Goals of the Project:** 
* To provide a seamless, rich-text note-editing experience.
* To integrate AI seamlessly for content generation and improvement.
* To ensure user data is safely stored locally with optional cloud backup and sync.
* To offer a highly customizable UI with themes and layout preferences.

---

## 2. App Features

* **Authentication (Google Sign-In):** Allows users to sign in securely using their Google account via Firebase Authentication and Android's Credential Manager. Ensures data can be tied to a specific user for cloud backup.
* **Note Creation:** Users can create new notes with a title and rich body content. 
* **Note Editing (Block Editor):** A rich text editor that allows users to modify existing notes, add formatting, and embed images.
* **AI Note Writing / AI Tools:** Utilizes Google's Gemini AI to assist users. Features include:
  * **Generate Title:** Automatically creates a catchy, short title based on the note's content.
  * **Summarize:** Condenses long notes into a brief summary.
  * **Fix Grammar:** Corrects grammatical errors and typos.
  * **Expand:** Elaborates on short ideas to generate longer text.
  * **Rewrite:** Rephrases the selected text for better flow.
  * **Continue Writing:** Generates the next few sentences based on the context of the note.
* **Categories / Tags:** Users can assign categories or tags to notes to keep them organized.
* **Reminders (Alarms):** Users can set exact date/time reminders for notes. Includes recurring options (Daily, Weekly, Monthly, Yearly).
* **Pin Notes:** Users can pin important notes to the top of their list for quick access.
* **Archive:** Users can move notes they don't currently need into an Archive tab, keeping the main view clutter-free.
* **Delete / Trash:** Notes are moved to a Trash bin when deleted, preventing accidental permanent deletion.
* **Restore:** Notes in the Trash can be restored back to the main list.
* **Profile:** Displays the currently logged-in user's profile picture, name, and email.
* **Settings:** Allows users to customize the app experience (Theme, Default Layout, Backup).
* **Theme (Light/Dark/System):** Users can switch between a Light theme, Dark theme, or follow the system's default setting.
* **Grid / List View:** Users can toggle the home screen layout between a staggered grid and a vertical list.
* **Ads (AdMob):** Monetization through Google AdMob, including Banner Ads, Interstitial Ads, and Native Ads embedded in the note list.
* **Offline Support:** All notes are saved to a local Room database, ensuring the app works perfectly without an internet connection.
* **Cloud Sync / Backup:** Users can manually (or automatically upon login) sync their local notes and settings to Firebase Firestore, allowing data recovery across devices.
* **In-App Browser:** For viewing Privacy Policies and other web links without leaving the app.

---

## 3. Screen Documentation

### AuthScreen
* **Purpose:** Handles user login and onboarding.
* **UI Components:** Welcome illustrations/Lottie animations, App Title, "Sign in with Google" button, Ad banner.
* **User Actions:** Click to sign in. 
* **Navigation Flow:** On successful sign-in, navigates to `HomeScreen`.

### HomeScreen
* **Purpose:** The main dashboard displaying all notes.
* **UI Components:** 
  * **Top App Bar:** Search bar, Profile picture icon.
  * **Tabs:** Notes, Reminders, Archive, Trash.
  * **Note List:** `LazyVerticalStaggeredGrid` showing note cards. Native ads are injected periodically.
  * **Bottom App Bar:** Navigation icons (Home, Settings) and a central Floating Action Button (FAB).
  * **Layout Toggle:** Button to switch between Grid and List view.
* **User Actions:** Search notes, switch tabs, click a note to edit, click FAB to create a new note, toggle layout.
* **Navigation Flow:** Navigates to `EditNoteScreen`, `SettingsScreen`, or `ProfileScreen`.

### EditNoteScreen
* **Purpose:** The workspace for writing and modifying a single note.
* **UI Components:**
  * **Top App Bar:** Back button, Pin toggle, Archive toggle, Reminder icon, Color picker, Delete button.
  * **Editor Area:** Title text field, Body text field (BlockEditor).
  * **AI Floating Action Button:** Expands to show AI options (Summarize, Fix Grammar, etc.).
  * **Bottom Bar:** Checkboxes, image attachments.
* **User Actions:** Type text, select colors, set reminders, trigger AI generation, pin/archive/delete note.
* **Navigation Flow:** Saves automatically on navigating back to `HomeScreen`.

### ProfileScreen
* **Purpose:** Shows user account details.
* **UI Components:** Profile picture (loaded via Coil), User Name, Email address, Logout button.
* **User Actions:** View account details, Sign out.
* **Navigation Flow:** Navigates back to `HomeScreen` or `AuthScreen` (if signed out).

### SettingsScreen
* **Purpose:** Manages app preferences and data backups.
* **UI Components:** 
  * Theme selection dropdown (System, Light, Dark).
  * Default View selection (Grid, List).
  * "Back Up Now" button.
  * "Recover Data" button.
  * Links to Privacy Policy.
* **User Actions:** Change theme, perform manual cloud sync, recover data from cloud, view policies.
* **Navigation Flow:** Opens `InAppBrowserScreen` for web links, or navigates back to `HomeScreen`.

### InAppBrowserScreen
* **Purpose:** Displays web pages inside the app.
* **UI Components:** `WebView`, Top App Bar with title and back button.

---

## 4. UI Design System

The app uses Google's **Material Design 3 (M3)** design system.

* **Primary Colors (`0xFF6750A4` Light / `0xFFD0BCFF` Dark):** Used for prominent UI elements like the Floating Action Button, active tab indicators, and primary buttons.
* **Secondary Colors (`0xFF625B71` Light / `0xFFCCC2DC` Dark):** Used for less prominent interactive elements and secondary text.
* **Tertiary Colors (`0xFF7D5260` Light / `0xFFEFB8C8` Dark):** Used for contrasting accents.
* **Background Colors (`0xFFFFFBFE` Light / `0xFF121212` Dark):** The underlying color of the app screens.
* **Surface Colors (`0xFFFFFBFE` Light / `0xFF1E1E1E` Dark):** Used for cards, sheets, and menus resting on top of the background.
* **Error Colors (`0xFFB3261E` Light / `0xFFF2B8B5` Dark):** Used for destructive actions (like Delete/Trash) and error messages.
* **Note Colors:** Users can select custom colors for individual notes. These colors are rendered with a slight opacity over the surface color to maintain readability.

**Why:** M3 colors provide built-in accessibility, contrast compliance, and a modern, cohesive look.

---

## 5. Typography

The app relies on the standard **Material 3 Typography** scale.

* **Fonts Used:** Default Android system font (Roboto/Inter depending on device).
* **Font Family:** `FontFamily.Default`.
* **Heading Styles (TitleLarge/TitleMedium):** Used for Note Titles, Screen Titles (e.g., "Settings"), and large empty-state texts.
* **Body Text Styles (BodyLarge - 16sp):** Used for the main content of notes and standard list items.
* **Labels/Captions (LabelSmall/BodySmall):** Used for timestamps, category tags, and bottom navigation labels.
* **Font Weights:** `FontWeight.Normal` for body text, `FontWeight.Medium` or `Bold` for titles and buttons.

---

## 6. Icons

* **Icon Library:** `androidx.compose.material.icons.extended` (Material Design Icons).
* **Icon Style:** Filled and Outlined (e.g., `Icons.Filled.Archive`, `Icons.AutoMirrored.Filled.ViewList`).
* **Icon Sizes:** Standard 24dp for most UI actions; slightly larger for FABs.
* **Where Icons are Used:** Top App Bars, Bottom Navigation Bars, Floating Action Buttons, AI Tool menus, Settings list items.

---

## 7. Images & Assets

* **Image Loading:** Uses **Coil** (`coil-compose`) for asynchronous image loading.
* **Profile Pictures:** Fetched via URL from the Google account profile.
* **Note Attachments:** Users can attach local images to notes, which are displayed using Compose `AsyncImage`.
* **Empty-state Graphics / Illustrations:** Used when lists (like Archive or Trash) are empty to provide visual feedback instead of a blank screen.

---

## 8. Theme System

* **Light Theme:** Bright backgrounds with dark text, utilizing the predefined `LightColorScheme`.
* **Dark Theme:** Dark grey/black backgrounds with light text, utilizing `DarkColorScheme`. Reduces eye strain and saves battery on OLED screens.
* **System Theme:** Automatically switches between Light and Dark based on the OS-level settings.
* **Implementation:** Controlled by `UserPreferencesRepository` (DataStore) and applied at the root of the app using `MyApplicationTheme` in `MainActivity`.

---

## 9. Navigation

* **Navigation Graph:** Managed by Jetpack Compose Navigation (`NavHost`).
* **Routes:** 
  * `auth`: Starting destination for unauthenticated users.
  * `home`: Starting destination for authenticated users.
  * `edit?noteId={noteId}`: Dynamic route passing the ID of the note to edit. If null, creates a new note.
  * `settings`: App preferences.
  * `profile`: User account screen.
* **Bottom Navigation:** A custom Bottom App Bar on the Home screen providing quick access to Home, FAB (Add), and Settings.
* **Back Navigation:** Handled properly using `navController.popBackStack()`. Saving notes automatically triggers on back press.

---

## 10. User Flow

1. **App Open** -> `MainActivity` checks Auth state.
2. **Login** -> If not logged in, user sees `AuthScreen`. Clicks Google Sign-In.
3. **Home** -> User sees `HomeScreen`. They can view Notes, Reminders, Archive, or Trash.
4. **Create Note** -> User clicks FAB -> Navigates to `EditNoteScreen`.
5. **Edit Note** -> User types title/content, optionally uses AI tools, picks a color, sets a reminder.
6. **Save** -> User presses Back. Note is saved to Room database. User returns to `Home`.
7. **Settings** -> User navigates to `SettingsScreen` to change theme or manually Sync data to cloud.
8. **Logout** -> User goes to `ProfileScreen`, clicks Logout, and is returned to `AuthScreen`.

---

## 11. AI Features

* **AI Provider:** Google Generative AI SDK (Gemini API).
* **AI Model:** `gemini-3.5-flash` (Optimized for fast, text-based tasks).
* **API Implementation:** Handled by `GenerativeAiProvider` as a Singleton. Uses a free-tier API key stored in `BuildConfig.GEMINI_API_KEY`.
* **Features:**
  1. **AI Note Writer / Continue Writing:** Predicts and generates the next logical sentences.
  2. **AI Summary:** Prompts the model to summarize the current text.
  3. **Fix Grammar:** Prompts the model to correct grammatical issues.
  4. **Expand & Rewrite:** Prompts the model to elaborate on or rephrase the text.
  5. **Generate Title:** Prompts the model to read the note body and suggest a 5-word catchy title.
* **Request Flow:** User selects an AI action in `EditNoteScreen` -> `EditNoteViewModel` constructs a specific prompt string -> Calls `GenerativeAiProvider.model.generateContent(prompt)` -> Returns text and updates the UI state.
* **Limitations:** Requires internet connection. Relies on the Gemini Free Tier limits.

---

## 12. Authentication

* **System:** Firebase Authentication.
* **Method:** Google Sign-In via `androidx.credentials` (Credential Manager API), which is the modern standard for Android 14+.
* **Session Management:** Firebase automatically manages session tokens. `FirebaseAuth.getInstance().currentUser` is checked on app launch.
* **User Profile:** Pulls Display Name, Email, and Photo URL from the Google account to display in the `ProfileScreen`.
* **Sign Out:** Clears the Firebase session and navigates the user back to the Auth screen.

---

## 13. Database

* **Database Technology:** Room Database (SQLite abstraction).
* **Tables:** `notes` table.
* **Stored Fields (`Note.kt`):**
  * `id` (String, Primary Key)
  * `title` (String)
  * `content` (String)
  * `color` (Int)
  * `category` (String)
  * `reminderTime` (Long, nullable)
  * `reminderRepeat` (String, nullable)
  * `isDone` (Boolean)
  * `isPinned` (Boolean)
  * `isArchived` (Boolean)
  * `isDeleted` (Boolean)
  * `createdAt` (Long)
  * `updatedAt` (Long)
* **Offline Caching:** Room is the single source of truth. All reads/writes happen instantly locally, allowing 100% offline usage.
* **Preferences:** Uses `SharedPreferences` / `DataStore` (via `UserPreferencesRepository`) for Theme, View mode, and Last Synced timestamp.

---

## 14. Cloud Services

* **Firebase Authentication:** Handles Google Sign-In and user identity.
* **Firebase Firestore:** A NoSQL cloud database used for **Backup and Sync**.
  * **Structure:** `/Users/{userId}/notes/{noteId}`.
  * **Sync Behavior:** Handled by `NotesRepository.syncAllNotesWithCloud()`. It compares local `updatedAt` timestamps with remote timestamps to bidirectionally merge data, ensuring the latest changes are kept across devices.
* **Firebase App Check (reCAPTCHA):** Configured to protect the backend from abuse.

---

## 15. Advertisement System

* **Integration:** Google AdMob SDK and UMP (User Messaging Platform) for EU consent.
* **Consent:** UMP SDK checks and requests user consent for personalized/non-personalized ads on app launch.
* **Banner Ads:** Placed at the bottom of the `AuthScreen` and potentially other lists.
* **Interstitial Ads:** Managed by `AdManager`. Tracks user navigation actions (e.g., changing screens). Once a random threshold (10-20 actions) is reached, a full-screen interstitial ad is shown, and the counter resets.
* **Native Ads:** Integrated directly into the `LazyVerticalStaggeredGrid` on the `HomeScreen`. Seamlessly blends with the note cards.
* **Ad Loading Strategy:** Interstitial and Native ads are pre-loaded in the background to ensure they display instantly when required without delaying the user experience.

---

## 16. Project Architecture

* **Architecture Pattern:** MVVM (Model-View-ViewModel) paired with Clean Architecture principles.
* **State Management:** Uses Kotlin `StateFlow` and `MutableStateFlow` in ViewModels to expose UI state to Compose screens.
* **Dependency Injection:** Manual DI using `AppContainer`. Avoids the overhead of Hilt/Dagger for a streamlined build, while keeping dependencies (Database, Repositories, Auth) decoupled and testable.
* **Folder Structure:**
  * `ai/`: Gemini API integration.
  * `data/`: Room DB, Entities, DAOs, Repositories (Firestore sync logic).
  * `di/`: AppContainer for Dependency Injection.
  * `reminders/`: BroadcastReceivers for alarms and boot completion.
  * `ui/`: Compose screens categorized by feature (`auth`, `home`, `edit`, `settings`, `profile`, `components`, `theme`).

---

## 17. Technologies Used

* **Programming Language:** Kotlin (Java 11 compatibility).
* **UI Framework:** Jetpack Compose (Material 3).
* **Build System:** Gradle (Kotlin DSL `build.gradle.kts`).
* **SDK Versions:** Min SDK 24 (Android 7.0), Target SDK 35 (Android 15).
* **Key Libraries & Packages:**
  * `androidx.compose.*` (UI)
  * `androidx.navigation.compose` (Routing)
  * `androidx.room.*` (Local DB)
  * `androidx.credentials` (Auth)
  * `com.google.firebase.*` (Auth, Firestore, AppCheck)
  * `com.google.ai.client.generativeai` (Gemini AI)
  * `io.coil-kt:coil-compose` (Image Loading)
  * `com.google.android.gms:play-services-ads` (AdMob)
  * `com.google.android.ump:user-messaging-platform` (Ad Consent)
  * `org.jetbrains.kotlinx:kotlinx-coroutines` (Async work)

---

## 18. Permissions

* `INTERNET` & `ACCESS_NETWORK_STATE`: Required for Firebase Auth, Firestore Sync, Gemini AI, and AdMob.
* `RECORD_AUDIO`: Likely used for voice-to-text input in the note editor (if implemented by the OS keyboard).
* `READ_EXTERNAL_STORAGE` / `READ_MEDIA_IMAGES`: Required to allow users to attach local images to their notes.
* `POST_NOTIFICATIONS`: Required for Android 13+ to show reminder notifications.
* `SCHEDULE_EXACT_ALARM`: Required to trigger time-sensitive note reminders reliably.
* `RECEIVE_BOOT_COMPLETED`: Required to reschedule alarms after the device reboots.
* `VIBRATE`: Used for haptic feedback during notifications.

---

## 19. Data Collection

* **What is collected:** Note data (title, content, categories, timestamps), User Email, Name, and Profile Picture URL.
* **Why it is collected:** To provide the core functionality of the app, sync data across devices, and display the user profile. AdMob collects device identifiers for ad targeting (subject to UMP consent).
* **Where it is stored:** Notes are stored locally on the device and securely backed up to Google Cloud (Firebase Firestore).
* **Third Parties:** Ad data is shared with Google AdMob. AI Prompts (note content sent for generation) are sent to Google Generative AI servers.
* **Privacy Considerations:** Users can delete their notes, which deletes them locally and triggers a delete on Firestore.

---

## 20. Security

* **Authentication Security:** Handled by Google via Credential Manager, preventing password interception.
* **API Security:** Gemini API Keys are hidden in `.env` and `local.properties`, preventing them from being checked into version control.
* **Secure Storage:** Firebase handles secure token management. 
* **Database Security:** Firestore Security Rules ensure that users can only read and write to their own specific `Users/{userId}` document path. `request.auth.uid == userId`.

---

## 21. Performance Optimizations

* **Lazy Loading:** `LazyVerticalStaggeredGrid` is used on the HomeScreen to only render note cards that are currently visible on the screen, saving memory.
* **Image Optimization:** Coil caches images locally and handles downsampling to prevent OutOfMemory errors when displaying large attachments.
* **Background Tasks:** All Database reads/writes, Firestore syncs, and AI API calls are executed on `Dispatchers.IO` using Kotlin Coroutines to keep the Main UI thread perfectly smooth.
* **Ad Pre-loading:** Interstitial and Native ads are fetched in the background before they are needed.

---

## 22. Error Handling

* **Network Errors:** App degrades gracefully. If offline, AI features show an error toast, but the core note-taking app continues to function perfectly using Room.
* **API Failures:** `try-catch` blocks in ViewModels handle AI generation failures and Firestore sync failures, updating the UI state to show a failure message rather than crashing.
* **Empty States:** The UI explicitly handles empty lists (e.g., "No archived notes") to guide the user, rather than showing a confusing blank screen.

---

## 23. Accessibility

* **Font Scaling:** Built using standard sp (scale-independent pixels) units, meaning text automatically scales if the user increases system font size.
* **Contrast:** Material 3 color palettes guarantee accessible contrast ratios between text and backgrounds in both light and dark modes.
* **Touch Targets:** Compose `IconButton` and standard components default to a minimum 48x48dp touch target, complying with accessibility guidelines.

---

## 24. Device Compatibility

* **Supported OS:** Android 7.0 (Nougat) and above (Min SDK 24).
* **Form Factors:** The use of `LazyVerticalStaggeredGrid` allows the app to dynamically adjust to different screen sizes. It works well on standard Phones. On Tablets and Foldable devices, the staggered grid will naturally fill the wider space with more columns.
* **Orientation:** Supports both Portrait and Landscape orientations out of the box due to Compose's declarative layout system.

---

## 25. Future Improvements

* **Rich Text Formatting:** Bold, Italic, Underline, and Bullet point support in the Block Editor.
* **Folders / Notebooks:** Grouping notes into distinct notebooks beyond just tags.
* **Collaborative Editing:** Allowing multiple users to edit a note simultaneously.
* **Export Options:** Export notes to PDF, TXT, or Markdown formats.
* **Voice Notes:** Built-in audio recording and transcription.

---

## 26. Project Summary

**TFPlans** is a robust, modern Android application designed for productivity. It offers a complete suite of note-taking tools enhanced by cutting-edge AI features (via Gemini API) that help users summarize, rewrite, and generate content effortlessly. 

Built with Kotlin and Jetpack Compose, the app boasts a beautiful, reactive, Material Design 3 interface that supports dynamic theming and layout switching. Under the hood, it utilizes Clean Architecture with MVVM, ensuring that the app is highly performant and maintainable. 

For users, it provides the reliability of a fully offline-capable Room database, combined with the security and convenience of Google Sign-In and Firestore cloud synchronization. For developers, it serves as an excellent reference architecture for integrating local databases, remote sync, AI APIs, and monetization (AdMob) into a single, cohesive Jetpack Compose application.
