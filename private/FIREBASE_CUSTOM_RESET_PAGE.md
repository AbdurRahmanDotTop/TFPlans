# Customizing the Firebase Password Reset Web Page

Yes! Aap Firebase ke default password reset web page ko apne brand (colors, logo, typography) ke hisaab se bilkul customize kar sakte hain. Default Firebase page (jo screenshot mein dikh raha hai) bahut basic hota hai, lekin Firebase aapko apna khud ka custom page use karne ki facility deta hai.

Ise set up karne ke liye aapko ek custom web page banana hoga aur Firebase ko batana hoga ki reset link us page par redirect ho. 

Neeche step-by-step process diya gaya hai:

## Step 1: Ek Custom Web Page Banayein
Aapko ek simple HTML, CSS, aur JavaScript ka page banana hoga jo user ka naya password accept karega aur Firebase API ka use karke password reset karega.

**Example HTML/JS Structure (`reset-password.html`):**

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Reset Your Password - Techily Fly</title>
    <!-- Apna custom CSS yahan add karein taake page aapke app jaisa dikhe -->
    <style>
        body { font-family: 'Arial', sans-serif; background-color: #F5F5F5; text-align: center; padding: 50px; }
        .container { background: white; padding: 30px; border-radius: 12px; box-shadow: 0 4px 10px rgba(0,0,0,0.1); max-width: 400px; margin: auto; }
        input { width: 100%; padding: 10px; margin: 10px 0; border: 1px solid #ccc; border-radius: 6px; }
        button { background-color: #6200EE; color: white; border: none; padding: 12px; border-radius: 6px; cursor: pointer; width: 100%; }
    </style>
</head>
<body>
    <div class="container">
        <h2>Techily Fly</h2>
        <h3>Set a New Password</h3>
        <input type="password" id="new-password" placeholder="Enter new password" />
        <button onclick="resetPassword()">Update Password</button>
        <p id="message"></p>
    </div>

    <!-- Firebase SDKs -->
    <script type="module">
        import { initializeApp } from "https://www.gstatic.com/firebasejs/10.7.1/firebase-app.js";
        import { getAuth, verifyPasswordResetCode, confirmPasswordReset } from "https://www.gstatic.com/firebasejs/10.7.1/firebase-auth.js";

        // Apna Firebase Config yahan dalein
        const firebaseConfig = {
            apiKey: "YOUR_API_KEY",
            authDomain: "techily-fly-15298.firebaseapp.com",
            projectId: "techily-fly-15298",
            // ... (baki config)
        };

        const app = initializeApp(firebaseConfig);
        const auth = getAuth(app);

        // URL se 'oobCode' (action code) extract karein
        const urlParams = new URLSearchParams(window.location.search);
        const actionCode = urlParams.get('oobCode');

        window.resetPassword = function() {
            const newPassword = document.getElementById('new-password').value;
            const messageEl = document.getElementById('message');
            
            if(!actionCode) {
                messageEl.innerText = "Invalid or expired link.";
                return;
            }

            confirmPasswordReset(auth, actionCode, newPassword)
                .then(() => {
                    messageEl.innerText = "Password has been reset successfully! You can now log into the app.";
                    messageEl.style.color = "green";
                })
                .catch((error) => {
                    messageEl.innerText = "Error: " + error.message;
                    messageEl.style.color = "red";
                });
        }
    </script>
</body>
</html>
```

## Step 2: Page ko Host Karein
Is page ko aapko internet par host karna hoga taake users isey access kar sakein. Aap isey kahin bhi host kar sakte hain:
- **Firebase Hosting** (Recommended: Agar aapka project pehle se Firebase par hai, to `firebase init hosting` aur `firebase deploy` use karein).
- Vercel, Netlify, Github Pages, ya aapki apni personal website (e.g., `techilyfly.com/reset-password`).

## Step 3: Firebase Console Mein Custom URL Set Karein
Jab aapka page live ho jaye (maan lijiye URL hai: `https://techilyfly.com/reset-password`), toh:

1. Apne **Firebase Console** mein jayein.
2. **Authentication** section kholiye, aur **Templates** tab par click karein.
3. Left menu se **Password Reset** template select karein.
4. "Action URL" (Customize action URL) wale section me **Edit** (pencil icon) par click karein.
5. Apni nai hosting link (e.g., `https://techilyfly.com/reset-password`) waha enter karein aur save karein.

## Ye Kaise Kaam Karega?
1. Jab koi user app mein "Forgot Password" karega, toh usko Firebase se ek email aayega.
2. Us email mein jo link hoga, wo aapke custom page (`https://techilyfly.com/reset-password?oobCode=XYZ...`) par jayega.
3. User aapka brand-new custom UI dekhega, password enter karega, aur Firebase JS SDK us `oobCode` ki madad se backend par password successfully update kar dega!
