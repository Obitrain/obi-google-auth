# @obitrain/react-native-google-auth

Google sign-in as a [Nitro module](https://nitro.margelo.com):
[GoogleSignIn SDK](https://github.com/google/GoogleSignIn-iOS) on iOS,
[Credential Manager](https://developer.android.com/identity/sign-in/credential-manager) +
[AuthorizationClient](https://developer.android.com/identity/authorization) on Android.
Alternative to [`@react-native-google-signin/google-signin`](https://github.com/react-native-google-signin/google-signin).

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
`noCredential` (no usable Google account / nothing to restore) | `error` (details in `errorMessage`).

### Beyond the basic flow

```ts
// extra scopes and/or a one-time code the backend exchanges for tokens
// (Android may show a consent screen after the account sheet)
const { user, serverAuthCode, grantedScopes } = await GoogleAuth.signIn({
  scopes: ['https://www.googleapis.com/auth/fitness.activity.read'],
  offlineAccess: true,
});

// no-UI sign-in for returning users (previously authorized account only)
const silent = await GoogleAuth.signInSilently();

// best-effort unlink of the app from the Google account
await GoogleAuth.revokeAccess();

// Android: verify Play Services, optionally showing Google's fix-it dialog
const ok = await GoogleAuth.checkPlayServices();
```

## Setup

**iOS** — the iOS client id is read from the app's `GoogleService-Info.plist`
(`CLIENT_ID`), or pass `iosClientId` to `configure`. The app must declare the
reversed client id as a URL scheme (`CFBundleURLSchemes`). No AppDelegate change:
the redirect is picked up from React Native's `RCTOpenURLNotification`.

**Android** — nothing beyond the dependency; no `google-services.json` requirement
and no Play Services version pin in the app's `build.gradle`. Sign-in is
Credential Manager (authentication only); `scopes` / `offlineAccess` run the
separate Authorization flow, so `serverAuthCode` comes from a second, cached
consent step rather than the sign-in sheet itself.

## Development

```sh
yarn            # install
yarn nitrogen   # regenerate nitrogen/ after touching src/*.nitro.ts
yarn typecheck
yarn prepare    # full bob build (nitrogen + module + types)
```

## License

MIT
