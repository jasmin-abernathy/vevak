import Foundation

struct TrustedContact: Identifiable, Codable, Equatable {
    let id: UUID
    var name: String
    var phoneNumber: String
    var isEmergencyRecipient: Bool

    init(
        id: UUID = UUID(),
        name: String,
        phoneNumber: String,
        isEmergencyRecipient: Bool = false
    ) {
        self.id = id
        self.name = name
        self.phoneNumber = phoneNumber
        self.isEmergencyRecipient = isEmergencyRecipient
    }
}

enum TrustedContactPolicy {
    static let maximumCount = 5

    static func normalizedName(_ value: String) -> String {
        value.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    static func normalizedPhoneNumber(_ value: String) -> String {
        value.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    static func canAdd(name: String, phoneNumber: String, currentCount: Int) -> Bool {
        currentCount < maximumCount
            && !normalizedName(name).isEmpty
            && !normalizedPhoneNumber(phoneNumber).isEmpty
    }
}
