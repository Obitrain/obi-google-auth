# @obitrain/react-native-google-auth

Google sign-in as a [Nitro module](https://nitro.margelo.com):
[GoogleSignIn SDK](https://github.com/google/GoogleSignIn-iOS) on iOS,
[Credential Manager](https://developer.android.com/identity/sign-in/credential-manager) on Android.
Alternative to [`@react-native-google-signin/google-signin`](https://github.com/react-native-google-signin/google-signin)
for apps that only need a Google ID token.

## Usage

```ts
import { GoogleAuth } from '@obitrain/react-native-google-auth';

// once at startup — webClientId is the OAuth *web* client id (token audience)
GoogleAuth.configure({ webClientId: GOOGLE_ID });

const { user, errorCode, errorMessage } = await GoogleAuth.signIn();
if (user) sendToBackend(user.idToken);

await GoogleAuth.signOut();
```

`errorCode`: `cancelled` | `inProgress` | `playServicesNotAvailable` (Android) |
`noCredential` (Android, no usable Google account) | `error` (details in `errorMessage`).

## Setup

**iOS** — the iOS client id is read from the app's `GoogleService-Info.plist`
(`CLIENT_ID`), or pass `iosClientId` to `configure`. The app must declare the
reversed client id as a URL scheme (`CFBundleURLSchemes`). No AppDelegate change:
the redirect is picked up from React Native's `RCTOpenURLNotification`.

**Android** — nothing beyond the dependency; no `google-services.json` requirement
and no Play Services version pin in the app's `build.gradle`.

## Development

```sh
yarn            # install
yarn nitrogen   # regenerate nitrogen/ after touching src/*.nitro.ts
yarn typecheck
yarn prepare    # full bob build (nitrogen + module + types)
```

## License

MIT
