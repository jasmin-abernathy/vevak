import SwiftUI

struct AddContactView: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var contactStore: TrustedContactStore

    @State private var name = ""
    @State private var phoneNumber = ""

    var body: some View {
        NavigationStack {
            Form {
                Section("Contact de confiance") {
                    TextField("Nom", text: $name)
                        .textContentType(.name)
                    TextField("Numéro de téléphone", text: $phoneNumber)
                        .textContentType(.telephoneNumber)
                        .keyboardType(.phonePad)
                }

                Section {
                    Text("VeVak conserve au maximum cinq contacts sur cet appareil. Le fichier local est protégé par iOS et exclu de la sauvegarde cloud de l’app.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Ajouter un contact")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Annuler") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Ajouter") {
                        if contactStore.add(name: name, phoneNumber: phoneNumber) {
                            dismiss()
                        }
                    }
                    .disabled(
                        !TrustedContactPolicy.canAdd(
                            name: name,
                            phoneNumber: phoneNumber,
                            currentCount: contactStore.contacts.count
                        )
                    )
                }
            }
        }
    }
}
