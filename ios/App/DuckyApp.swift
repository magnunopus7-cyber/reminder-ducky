import SwiftUI
import UserNotifications

@main
struct DuckyApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            ContentView()
                .onChange(of: scenePhase) { _, phase in
                    if phase == .active { Notifications.sync() }
                }
        }
    }
}

final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        Notifications.setUp(delegate: self)
        MainActor.assumeIsolated { PreviewMode.run() }
        return true
    }

    // A reminder arrives while the app is open: still show it and quack.
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        NotificationCenter.default.post(name: .duckyReminderArrived, object: nil)
        SharedStore.reloadWidgets()
        completionHandler([.banner, .list, .sound])
    }

    // Done or Snooze pressed on a notification.
    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse,
                                withCompletionHandler completionHandler: @escaping () -> Void) {
        let id = (response.notification.request.content.userInfo["id"] as? NSNumber)?.int64Value
        if let id {
            switch response.actionIdentifier {
            case Notifications.doneAction:
                SharedStore.complete(id: id)
                Notifications.sync()
            case Notifications.snoozeAction:
                Notifications.snooze(id: id)
            default:
                break
            }
        }
        NotificationCenter.default.post(name: .duckyRemindersChanged, object: nil)
        SharedStore.reloadWidgets()
        completionHandler()
    }
}

extension Notification.Name {
    static let duckyReminderArrived = Notification.Name("duckyReminderArrived")
    static let duckyRemindersChanged = Notification.Name("duckyRemindersChanged")
}
