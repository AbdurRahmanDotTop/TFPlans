# Google Cloud & Drive API Setup Guide

This document records the exact manual steps followed to configure the Google Cloud Console for the TF Plans Android app, specifically to enable Google Drive backup and synchronization.

## 1. Firebase & SHA-1 Verification
Before configuring Google Cloud, it is essential to ensure Firebase is correctly linked to the app.
- Ensure the app is registered in the Firebase Console.
- Add **both** the Debug and Release SHA-1 fingerprints in the Firebase Project Settings.
- Firebase automatically creates the corresponding Android OAuth 2.0 Client IDs in the Google Cloud Console.

## 2. Enabling the Google Drive API
By default, the Google Drive API is disabled in new Google Cloud projects. If it is disabled, the app will throw a `403 Forbidden` error (which previously manifested as a "key error").
1. Go to the [Google Cloud Console](https://console.cloud.google.com).
2. Select your project (`techily-fly-15298`).
3. Navigate to **APIs & Services** > **Enabled APIs & services**.
4. Click the **+ ENABLE APIS AND SERVICES** button at the top of the page.
5. Search for **Google Drive API**.
6. Click on the result and click the blue **Enable** button.

## 3. Adding the Drive Scope to the Google Auth Platform
To request permission from the user to access their Google Drive, the specific Drive scope must be declared in the app's OAuth Consent Screen (now called Google Auth Platform).
1. In the Google Cloud Console, navigate to **Google Auth Platform** > **Data access** (previously "OAuth Consent Screen").
2. Click the **Add or remove scopes** button.
3. A right-side panel will appear titled "Update selected scopes".
4. In the **Filter** text box, type exactly: `drive.file`
5. Locate the row for the **Google Drive API** with the scope `https://www.googleapis.com/auth/drive.file`.
6. Check the checkbox next to this scope.
7. Click the **Update** button at the bottom of the side panel.
8. Scroll to the bottom of the main Data access page and click **Save**.

## 4. Testing the Integration
1. Run the Android app (ensure the Google Services JSON is updated if you recently added SHA fingerprints).
2. Tap **Sign in with Google**.
3. A consent screen should appear asking the user to allow "TF Plans" to view and manage Google Drive files.
4. Once authorized, the app will automatically create the `TF Plans` root folder and required subfolders in the user's Google Drive.
