# Fixing Google Sign-In Issue on Amazon Appstore

## The Core Problem
The error message you received is:
> `Google Sign-In failed. Error: getCredentialAsync no provider dependencies found - please ensure the desired provider dependencies are added`

**Why is this happening?**
Amazon devices (Fire Tablets, Fire TV) use an operating system called **Fire OS**. Unlike standard Android phones, Fire OS does **not** have **Google Play Services** installed. 

Your app currently uses Android's `CredentialManager` and Google Sign-in (`com.google.android.libraries.identity.googleid`), both of which **strictly require Google Play Services** to function. When the app tries to launch Google Sign-In on an Amazon device, it cannot find the necessary Google background services, resulting in the "no provider dependencies found" error.

## The Solution
You cannot make native Google Sign-In work on devices that don't have Google Play Services. To support the Amazon Appstore, you MUST provide an alternative way for users to log in.

### Step 1: Update the Error Message (Code Fixed)
I have updated `AuthViewModel.kt` to catch this specific error and show a user-friendly message instead of a technical crash-like text. It will now say:
*"Google Sign-In is not supported on this device (Google Play Services missing)."*

### Step 2: Implement an Alternative Login Method (Manual Action Required)
Since Amazon users cannot use Google Sign-In, you need to give them another option. The easiest approach since you are using Firebase is **Email/Password Authentication** or **Anonymous (Guest) Authentication**.

**Manual Steps to fix this:**
1. Go to the **Firebase Console** (console.firebase.google.com).
2. Go to **Authentication** -> **Sign-in method**.
3. Enable **Email/Password** provider (or Anonymous provider).
4. Update your `AuthScreen.kt` and `AuthViewModel.kt` to include fields for Email and Password. 
5. Add a "Sign in with Email" button alongside the "Continue with Google" button.

### Step 3: (Optional) Hide Google Sign-in on Amazon Devices
You can check if Google Play Services is available on the device before showing the "Continue with Google" button. If it's not available (like on Amazon devices), you can completely hide the Google button so users only see the Email login option.

To do this, you would use `GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)` from the `play-services-base` dependency.

---
**Conclusion:** I have handled the crash and error message for you, but you **must** add an Email/Password login UI to your app if you want users on the Amazon Appstore to be able to log in.
