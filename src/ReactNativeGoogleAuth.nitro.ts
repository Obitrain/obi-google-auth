import type { HybridObject } from 'react-native-nitro-modules';

export type GoogleAuthErrorCode =
  /** User dismissed the account picker / browser sheet. */
  | 'cancelled'
  /** A sign-in is already running ('inProgress' is only produced by the JS wrapper). */
  | 'inProgress'
  /** Android: no Credential Manager provider available (no/outdated Play Services). */
  | 'playServicesNotAvailable'
  /** Android: no Google account usable on the device. */
  | 'noCredential'
  | 'error';

export interface GoogleAuthUser {
  /** Google ID token (JWT); its audience is the configured web client id. */
  idToken: string;
  email?: string;
  name?: string;
  photoUrl?: string;
}

export interface GoogleSignInResult {
  user?: GoogleAuthUser;
  errorCode?: GoogleAuthErrorCode;
  /** Native error description when errorCode is set. */
  errorMessage?: string;
}

export interface ReactNativeGoogleAuth
  extends HybridObject<{ ios: 'swift'; android: 'kotlin' }> {
  /**
   * webClientId is the OAuth web client id — the audience of the returned ID
   * token. iOS also needs the iOS client id: pass iosClientId or leave it
   * undefined to fall back to CLIENT_ID from the bundled GoogleService-Info.plist.
   */
  configure(webClientId: string, iosClientId?: string): void;

  /** Opens the native account picker and resolves with a user or a typed error code. */
  signIn(): Promise<GoogleSignInResult>;

  /** iOS: GIDSignIn signOut. Android: clears the Credential Manager state. */
  signOut(): Promise<void>;
}
