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

  func signIn() throws -> Promise<GoogleSignInResult> {
    let promise = Promise<GoogleSignInResult>()
    DispatchQueue.main.async {
      guard let presenter = Self.topViewController() else {
        promise.resolve(
          withResult: GoogleSignInResult(
            user: nil, errorCode: .error, errorMessage: "No presenting view controller"))
        return
      }
      GIDSignIn.sharedInstance.signIn(withPresenting: presenter) { result, error in
        if let error {
          let cancelled = (error as? GIDSignInError)?.code == .canceled
          promise.resolve(
            withResult: GoogleSignInResult(
              user: nil,
              errorCode: cancelled ? .cancelled : .error,
              errorMessage: error.localizedDescription))
          return
        }
        guard let user = result?.user, let idToken = user.idToken?.tokenString else {
          promise.resolve(
            withResult: GoogleSignInResult(
              user: nil, errorCode: .error, errorMessage: "Sign-in returned no ID token"))
          return
        }
        promise.resolve(
          withResult: GoogleSignInResult(
            user: GoogleAuthUser(
              idToken: idToken,
              email: user.profile?.email,
              name: user.profile?.name,
              photoUrl: user.profile?.imageURL(withDimension: 120)?.absoluteString),
            errorCode: nil,
            errorMessage: nil))
      }
    }
    return promise
  }

  func signOut() throws -> Promise<Void> {
    GIDSignIn.sharedInstance.signOut()
    return Promise.resolved(withResult: ())
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
