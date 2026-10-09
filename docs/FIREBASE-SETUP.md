# Firebase setup

Project: `paydar-hydra`. Hosting: `paydar.web.app`. Android package: `com.paydar.app`.

Google sign-in is enabled. The supplied Android configuration includes OAuth clients, and the beta signing SHA-1 is registered. No service-account key is needed in the app.

## On your phone

1. Download and install the current APK from https://paydar.web.app on Android 8+ with Google Play services.
2. Open **Account → Continue with Google** while online.
3. Choose your Google account. Check that your name and email appear.
4. Sign out and sign in again. Local payment tools work without an account. Local records are shared by all accounts on this installation; they are not cloud-synced.
5. To remove the Firebase identity, select **Delete cloud account** and confirm the same Google account. Delete local records separately.

## Building with another keystore

Register your debug/release certificate SHA-1 in Firebase Project settings → Android app. Google Play distribution also requires the Play App Signing SHA-1. Only project administrators can change this configuration.

```sh
npx -y firebase-tools@latest apps:android:sha:create 1:771322522759:android:10845627bcca4754a29c22 YOUR_SHA1 --project paydar-hydra
```

The Firebase client config identifies this project; it does not grant administrative access. Forks should create their own Firebase project, Android app and Google provider, then replace `google-services.json`, `.firebaserc` and `firebase.json`.

## Deploy

```sh
npx -y firebase-tools@latest login
npx -y firebase-tools@latest deploy --only auth,hosting --project paydar-hydra
```

No Firestore or financial cloud database is provisioned. Firebase retains account identity until deletion. Before production, test login, cancellation, loss of network, sign-out and account deletion on a physical phone. Automated tests cannot prove those device flows work.

## APK delivery on the free Firebase plan

Firebase Spark Hosting disallows APK executable uploads. `firebase.json` excludes APK files and redirects `/downloads/PayDar-beta.apk` to the GitHub Release asset. Upload a new versioned GitHub release and update the redirect for each release; do not enable billing just to serve the beta.
