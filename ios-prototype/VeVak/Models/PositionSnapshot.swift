import Foundation

struct PositionSnapshot: Equatable {
    let latitude: Double
    let longitude: Double
    let horizontalAccuracyMeters: Double
    let timestamp: Date
}

struct MessageDraft: Identifiable, Equatable {
    let id = UUID()
    let recipients: [String]
    let body: String
}

enum ShareMessageBuilder {
    static func manual(snapshot: PositionSnapshot) -> String {
        "Ma position via VeVak : \(mapLink(snapshot: snapshot))\nPrécision indiquée par l’iPhone : ±\(roundedAccuracy(snapshot.horizontalAccuracyMeters)) m."
    }

    static func emergency(snapshot: PositionSnapshot) -> String {
        "Urgence VeVak — j’ai préparé ce message volontairement depuis mon iPhone.\nPosition : \(mapLink(snapshot: snapshot))\nPrécision indiquée : ±\(roundedAccuracy(snapshot.horizontalAccuracyMeters)) m.\nCe message n’est pas une preuve de livraison et VeVak ne contacte pas les secours."
    }

    static func mapLink(snapshot: PositionSnapshot) -> String {
        let latitude = String(format: "%.6f", locale: Locale(identifier: "en_US_POSIX"), snapshot.latitude)
        let longitude = String(format: "%.6f", locale: Locale(identifier: "en_US_POSIX"), snapshot.longitude)
        return "https://www.openstreetmap.org/?mlat=\(latitude)&mlon=\(longitude)#map=17/\(latitude)/\(longitude)"
    }

    private static func roundedAccuracy(_ value: Double) -> Int {
        max(0, Int(value.rounded()))
    }
}
