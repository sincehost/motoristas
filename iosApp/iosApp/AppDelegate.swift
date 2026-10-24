import UIKit
import BackgroundTasks
import FirebaseCore
import FirebaseMessaging
import ComposeApp

/// Configura o Firebase e liga o fluxo de push notifications (FCM + APNs).
/// Ver composeApp/src/iosMain/kotlin/.../PushTokenBridge.kt — é o ponto de
/// entrada do token de push no lado Kotlin.
class AppDelegate: UIResponder, UIApplicationDelegate, UNUserNotificationCenterDelegate, MessagingDelegate {

    /// Precisa ser igual ao de BGTaskSchedulerPermittedIdentifiers no Info.plist.
    static let idTarefaSincronizacao = "br.com.lfsystem.app.Motorista.sincronizacao"

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        FirebaseApp.configure()
        Messaging.messaging().delegate = self
        UNUserNotificationCenter.current().delegate = self

        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound]) { _, _ in }
        application.registerForRemoteNotifications()

        // Envio dos lançamentos pendentes com o app fora da tela (o Android já
        // fazia isso com o WorkManager). O registro tem que acontecer antes do
        // fim deste método. Fila principal: o lado Kotlin usa o mesmo banco
        // da tela e espera rodar na thread principal.
        BGTaskScheduler.shared.register(
            forTaskWithIdentifier: Self.idTarefaSincronizacao,
            using: DispatchQueue.main
        ) { tarefa in
            self.executarTarefaSincronizacao(tarefa)
        }
        NotificationCenter.default.addObserver(
            self,
            selector: #selector(entrouEmSegundoPlano),
            name: UIApplication.didEnterBackgroundNotification,
            object: nil
        )
        return true
    }

    // MARK: - Envio em segundo plano

    /// Motorista saiu do app (ex.: salvou o abastecimento e foi pro WhatsApp):
    /// pede ao iOS uns segundos pra terminar o envio e agenda a tarefa
    /// periódica pra tentar de novo se não der tempo.
    @objc private func entrouEmSegundoPlano() {
        guard SincronizacaoSegundoPlano.shared.temPendencias() else { return }
        agendarTarefaSincronizacao()

        var idTarefa: UIBackgroundTaskIdentifier = .invalid
        let encerrar: () -> Void = {
            guard idTarefa != .invalid else { return }
            UIApplication.shared.endBackgroundTask(idTarefa)
            idTarefa = .invalid
        }
        idTarefa = UIApplication.shared.beginBackgroundTask(withName: "sincronizacao") {
            SincronizacaoSegundoPlano.shared.cancelar()
            encerrar()
        }
        SincronizacaoSegundoPlano.shared.executar { _ in encerrar() }
    }

    /// Tarefa periódica — o iOS decide quando roda (normalmente a cada 15 min
    /// ou mais, conforme o uso do aparelho). Só agenda se houver pendência.
    private func agendarTarefaSincronizacao() {
        let pedido = BGAppRefreshTaskRequest(identifier: Self.idTarefaSincronizacao)
        pedido.earliestBeginDate = Date(timeIntervalSinceNow: 15 * 60)
        try? BGTaskScheduler.shared.submit(pedido)
    }

    private func executarTarefaSincronizacao(_ tarefa: BGTask) {
        var concluida = false
        let concluir: (Bool) -> Void = { sucesso in
            guard !concluida else { return }
            concluida = true
            if SincronizacaoSegundoPlano.shared.temPendencias() {
                self.agendarTarefaSincronizacao()
            }
            tarefa.setTaskCompleted(success: sucesso)
        }
        tarefa.expirationHandler = {
            SincronizacaoSegundoPlano.shared.cancelar()
            concluir(false)
        }
        SincronizacaoSegundoPlano.shared.executar { ok in concluir(ok.boolValue) }
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
