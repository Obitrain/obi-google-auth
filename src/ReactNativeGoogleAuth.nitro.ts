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

export interface GoogleSignInOptions {
  /** Extra OAuth scopes to request (openid/email/profile are implicit). */
  scopes?: string[];
  /**
   * Request a one-time server auth code (result.serverAuthCode) the backend can
   * exchange for access/refresh tokens. On Android this runs the Authorization
   * flow after sign-in, which may show a consent screen.
   */
  offlineAccess?: boolean;
}

export interface GoogleAuthUser {
  /** Google ID token (JWT); its audience is the configured web client id. */
  idToken: string;
  email?: string;
  name?: string;
  givenName?: string;
  familyName?: string;
  photoUrl?: string;
}

export interface GoogleSignInResult {
  user?: GoogleAuthUser;
  /** One-time code for backend token exchange; set when offlineAccess was requested. */
  serverAuthCode?: string;
  /** Scopes actually granted, when known (iOS always; Android when authorization ran). */
  grantedScopes?: string[];
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
  signIn(options?: GoogleSignInOptions): Promise<GoogleSignInResult>;

  /**
   * Sign in without UI when possible: previously-authorized account only
   * (iOS keychain session / Android auto-select). Resolves with 'noCredential'
   * when there is nothing to restore.
   */
  signInSilently(): Promise<GoogleSignInResult>;

  /** iOS: GIDSignIn signOut. Android: clears the Credential Manager state. */
  signOut(): Promise<void>;

  /**
   * Best-effort unlink of the app from the Google account: iOS disconnect();
   * Android revokes the silently-obtainable access token then clears
   * credential state. Always resolves.
   */
  revokeAccess(): Promise<void>;

  /**
   * Android: whether Play Services are usable; with showDialog, shows Google's
   * resolution dialog and resolves once available. iOS: always true.
   */
  checkPlayServices(showDialog: boolean): Promise<boolean>;
}
