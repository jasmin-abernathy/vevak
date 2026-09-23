import SwiftUI

@main
struct VeVakApp: App {
    @StateObject private var contactStore = TrustedContactStore()
    @StateObject private var locationService = LocationService()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(contactStore)
                .environmentObject(locationService)
                .tint(VeVakTheme.blue)
        }
    }
}
