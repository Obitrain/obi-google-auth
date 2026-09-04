import { NitroModules } from 'react-native-nitro-modules';
import type {
  GoogleAuthErrorCode,
  GoogleAuthUser,
  GoogleSignInOptions,
  GoogleSignInResult,
  ReactNativeGoogleAuth,
} from './ReactNativeGoogleAuth.nitro';

export type {
  GoogleAuthErrorCode,
  GoogleAuthUser,
  GoogleSignInOptions,
  GoogleSignInResult,
};

const native = NitroModules.createHybridObject<ReactNativeGoogleAuth>(
  'ReactNativeGoogleAuth'
);

export interface GoogleAuthConfig {
  webClientId: string;
  /** iOS only; falls back to CLIENT_ID from GoogleService-Info.plist. */
  iosClientId?: string;
}

let inFlight = false;

export const GoogleAuth = {
  configure(config: GoogleAuthConfig): void {
    native.configure(config.webClientId, config.iosClientId);
  },

  async signIn(options?: GoogleSignInOptions): Promise<GoogleSignInResult> {
    if (inFlight) return { errorCode: 'inProgress' };
    inFlight = true;
    try {
      return await native.signIn(options);
    } finally {
      inFlight = false;
    }
  },

  async signInSilently(): Promise<GoogleSignInResult> {
    if (inFlight) return { errorCode: 'inProgress' };
    inFlight = true;
    try {
      return await native.signInSilently();
    } finally {
      inFlight = false;
    }
  },

  signOut(): Promise<void> {
    return native.signOut();
  },

  revokeAccess(): Promise<void> {
    return native.revokeAccess();
  },

  checkPlayServices(showDialog = true): Promise<boolean> {
    return native.checkPlayServices(showDialog);
  },
};
