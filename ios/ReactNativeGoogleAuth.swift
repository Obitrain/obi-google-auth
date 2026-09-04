import Foundation
import GoogleSignIn
import NitroModules
import UIKit

/** Nitro bridge over the GoogleSignIn iOS SDK. */
class ReactNativeGoogleAuth: HybridReactNativeGoogleAuthSpec {
  private var urlObserver: NSObjectProtocol?

  deinit {
    if let urlObserver {
      NotificationCenter.default.removeObserver(urlObserver)
    }
  }

  func configure(webClientId: String, iosClientId: String?) throws {
    guard let clientId = iosClientId ?? Self.plistClientId() else {
      throw RuntimeError.error(
        withMessage:
          "No iOS client id: pass iosClientId or bundle a GoogleService-Info.plist with CLIENT_ID")
    }
    GIDSignIn.sharedInstance.configuration = GIDConfiguration(
      clientID: clientId, serverClientID: webClientId)

    // The browser redirect comes back through the app's URL scheme;
    // RCTLinkingManager re-posts every openURL as this notification.
    if urlObserver == nil {
      urlObserver = NotificationCenter.default.addObserver(
        forName: NSNotification.Name("RCTOpenURLNotification"), object: nil, queue: .main
      ) { note in
        guard let urlString = note.userInfo?["url"] as? String,
          let url = URL(string: urlString)
        else { return }
        _ = GIDSignIn.sharedInstance.handle(url)
      }
    }
  }

  func signIn(options: GoogleSignInOptions?) throws -> Promise<GoogleSignInResult> {
    let promise = Promise<GoogleSignInResult>()
    let wantsAuthCode = options?.offlineAccess == true
    let scopes = options?.scopes
    DispatchQueue.main.async {
      guard let presenter = Self.topViewController() else {
        promise.resolve(withResult: Self.errorResult(.error, "No presenting view controller"))
        return
      }
      GIDSignIn.sharedInstance.signIn(
        withPresenting: presenter, hint: nil, additionalScopes: scopes
      ) { result, error in
        if let error {
          promise.resolve(withResult: Self.errorResult(from: error))
          return
        }
        guard let result, let user = Self.toUser(result.user) else {
          promise.resolve(withResult: Self.errorResult(.error, "Sign-in returned no ID token"))
          return
        }
        promise.resolve(
          withResult: GoogleSignInResult(
            user: user,
            serverAuthCode: wantsAuthCode ? result.serverAuthCode : nil,
            grantedScopes: result.user.grantedScopes,
            errorCode: nil,
            errorMessage: nil))
      }
    }
    return promise
  }

  func signInSilently() throws -> Promise<GoogleSignInResult> {
    let promise = Promise<GoogleSignInResult>()
    DispatchQueue.main.async {
      GIDSignIn.sharedInstance.restorePreviousSignIn { user, error in
        if let error {
          promise.resolve(withResult: Self.errorResult(from: error))
          return
        }
        // restore can hand back an expired ID token — refresh before returning
        guard let user else {
          promise.resolve(withResult: Self.errorResult(.nocredential, "No previous sign-in"))
          return
        }
        user.refreshTokensIfNeeded { refreshed, refreshError in
          if let refreshError {
            promise.resolve(withResult: Self.errorResult(from: refreshError))
            return
          }
          guard let refreshed, let result = Self.toUser(refreshed) else {
            promise.resolve(withResult: Self.errorResult(.error, "Restored session has no ID token"))
            return
          }
          promise.resolve(
            withResult: GoogleSignInResult(
              user: result,
              serverAuthCode: nil,
              grantedScopes: refreshed.grantedScopes,
              errorCode: nil,
              errorMessage: nil))
        }
      }
    }
    return promise
  }

  func signOut() throws -> Promise<Void> {
    GIDSignIn.sharedInstance.signOut()
    return Promise.resolved(withResult: ())
  }

  func revokeAccess() throws -> Promise<Void> {
    let promise = Promise<Void>()
    DispatchQueue.main.async {
      // best-effort: resolve either way, nothing actionable for the caller
      GIDSignIn.sharedInstance.disconnect { _ in
        promise.resolve()
      }
    }
    return promise
  }

  func checkPlayServices(showDialog: Bool) throws -> Promise<Bool> {
    return Promise.resolved(withResult: true)
  }

  private static func toUser(_ user: GIDGoogleUser) -> GoogleAuthUser? {
    guard let idToken = user.idToken?.tokenString else { return nil }
    return GoogleAuthUser(
      idToken: idToken,
      email: user.profile?.email,
      name: user.profile?.name,
      givenName: user.profile?.givenName,
      familyName: user.profile?.familyName,
      photoUrl: user.profile?.imageURL(withDimension: 120)?.absoluteString)
  }

  private static func errorResult(from error: Error) -> GoogleSignInResult {
    let code: GoogleAuthErrorCode
    switch (error as? GIDSignInError)?.code {
    case .canceled: code = .cancelled
    case .hasNoAuthInKeychain: code = .nocredential
    default: code = .error
    }
    return errorResult(code, error.localizedDescription)
  }

  private static func errorResult(_ code: GoogleAuthErrorCode, _ message: String)
    -> GoogleSignInResult
  {
    return GoogleSignInResult(
      user: nil, serverAuthCode: nil, grantedScopes: nil, errorCode: code, errorMessage: message)
  }

  private static func plistClientId() -> String? {
    guard
      let path = Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist"),
      let dict = NSDictionary(contentsOfFile: path)
    else { return nil }
    return dict["CLIENT_ID"] as? String
  }

  private static func topViewController() -> UIViewController? {
    let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
    let window =
      scenes.flatMap { $0.windows }.first { $0.isKeyWindow } ?? scenes.first?.windows.first
    var top = window?.rootViewController
    while let presented = top?.presentedViewController {
      top = presented
    }
    return top
  }
}
