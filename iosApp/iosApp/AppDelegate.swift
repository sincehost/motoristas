import UIKit
import FirebaseCore
import FirebaseMessaging
import ComposeApp

/// Configura o Firebase e liga o fluxo de push notifications (FCM + APNs).
/// Ver composeApp/src/iosMain/kotlin/.../PushTokenBridge.kt — é o ponto de
/// entrada do token de push no lado Kotlin.
class AppDelegate: UIResponder, UIApplicationDelegate, UNUserNotificationCenterDelegate, MessagingDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        FirebaseApp.configure()
        Messaging.messaging().delegate = self
        UNUserNotificationCenter.current().delegate = self

        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound]) { _, _ in }

        // FORÇA UMA VEZ SÓ: descarta qualquer token FCM antigo (o Firebase
        // guarda isso no Keychain, que sobrevive a fechar/abrir o app e a
        // atualizações via TestFlight — por isso instalações que já tinham
        // rodado com aps-environment=development continuavam presas num
        // registro velho mesmo depois da correção pra production). Sem
        // isso o app nunca reemite um token novo sozinho.
        // TODO: remover esta chamada a deleteToken depois que essa build
        // rodar nos aparelhos já instalados (ver conversa sobre push).
        Messaging.messaging().deleteToken { _ in
            DispatchQueue.main.async {
                application.registerForRemoteNotifications()
            }
        }
        return true
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        Messaging.messaging().apnsToken = deviceToken
    }

    // Dispara sempre que o token FCM é emitido/renovado.
    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let fcmToken else { return }
        PushTokenBridge.shared.onTokenReceived(token: fcmToken)
    }

    // Notificação chegando com o app em primeiro plano — mostra normalmente.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .badge, .sound])
    }
}
