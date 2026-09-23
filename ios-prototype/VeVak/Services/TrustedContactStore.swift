import Foundation
import Combine

@MainActor
final class TrustedContactStore: ObservableObject {
    @Published private(set) var contacts: [TrustedContact] = []
    @Published private(set) var persistenceError: String?

    private let fileManager: FileManager
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()
    private let storageURL: URL

    init(fileManager: FileManager = .default) {
        self.fileManager = fileManager

        let base = fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? fileManager.temporaryDirectory
        let directory = base.appendingPathComponent("VeVak", isDirectory: true)
        self.storageURL = directory.appendingPathComponent("trusted-contacts.json", isDirectory: false)

        do {
            try fileManager.createDirectory(at: directory, withIntermediateDirectories: true)
            var values = URLResourceValues()
            values.isExcludedFromBackup = true
            var mutableDirectory = directory
            try mutableDirectory.setResourceValues(values)
            contacts = try loadContacts()
        } catch {
            persistenceError = "Impossible de charger le stockage local."
        }
    }

    var canAddContact: Bool {
        contacts.count < TrustedContactPolicy.maximumCount
    }

    var emergencyRecipients: [TrustedContact] {
        contacts.filter(\.isEmergencyRecipient)
    }

    @discardableResult
    func add(name: String, phoneNumber: String) -> Bool {
        guard TrustedContactPolicy.canAdd(
            name: name,
            phoneNumber: phoneNumber,
            currentCount: contacts.count
        ) else {
            return false
        }

        let previous = contacts
        contacts.append(
            TrustedContact(
                name: TrustedContactPolicy.normalizedName(name),
                phoneNumber: TrustedContactPolicy.normalizedPhoneNumber(phoneNumber)
            )
        )
        guard persist() else {
            contacts = previous
            return false
        }
        return true
    }

    func delete(id: UUID) {
        let previous = contacts
        contacts.removeAll { $0.id == id }
        if !persist() {
            contacts = previous
        }
    }

    func setEmergencyRecipient(id: UUID, enabled: Bool) {
        guard let index = contacts.firstIndex(where: { $0.id == id }) else { return }
        let previous = contacts
        contacts[index].isEmergencyRecipient = enabled
        if !persist() {
            contacts = previous
        }
    }

    private func loadContacts() throws -> [TrustedContact] {
        guard fileManager.fileExists(atPath: storageURL.path) else { return [] }
        let data = try Data(contentsOf: storageURL)
        let decoded = try decoder.decode([TrustedContact].self, from: data)
        return Array(decoded.prefix(TrustedContactPolicy.maximumCount))
    }

    @discardableResult
    private func persist() -> Bool {
        do {
            let data = try encoder.encode(contacts)
            try data.write(to: storageURL, options: [.atomic, .completeFileProtection])
            persistenceError = nil
            return true
        } catch {
            persistenceError = "Impossible d’enregistrer les contacts localement."
            return false
        }
    }
}
