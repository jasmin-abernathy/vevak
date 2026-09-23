import MessageUI
import SwiftUI

struct ContentView: View {
    private enum PendingAction {
        case manual(TrustedContact)
        case emergency
    }

    @EnvironmentObject private var contactStore: TrustedContactStore
    @EnvironmentObject private var locationService: LocationService

    @State private var showingAddContact = false
    @State private var pendingAction: PendingAction?
    @State private var showingConfirmation = false
    @State private var messageDraft: MessageDraft?
    @State private var errorMessage: String?

    var body: some View {
        NavigationStack {
            List {
                platformNotice
                contactsSection
                emergencySection
                privacySection
            }
            .navigationTitle("VeVak")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        showingAddContact = true
                    } label: {
                        Label("Ajouter un contact", systemImage: "person.badge.plus")
                    }
                    .disabled(!contactStore.canAddContact)
                }
            }
            .sheet(isPresented: $showingAddContact) {
                AddContactView()
                    .environmentObject(contactStore)
            }
            .sheet(item: $messageDraft) { draft in
                MessageComposeView(draft: draft) {
                    messageDraft = nil
                }
                .ignoresSafeArea()
            }
            .alert("Confirmer le partage", isPresented: $showingConfirmation) {
                Button("Annuler", role: .cancel) {
                    pendingAction = nil
                }
                Button("Continuer") {
                    if let action = pendingAction {
                        pendingAction = nil
                        requestPosition(for: action)
                    }
                }
            } message: {
                Text(confirmationMessage)
            }
            .alert("VeVak", isPresented: errorPresentedBinding) {
                Button("OK", role: .cancel) {
                    errorMessage = nil
                }
            } message: {
                Text(errorMessage ?? "")
            }
        }
    }

    private var platformNotice: some View {
        Section {
            VStack(alignment: .leading, spacing: 10) {
                Label("Version iPhone — prototype", systemImage: "iphone")
                    .font(.headline)
                    .foregroundStyle(VeVakTheme.blue)
                Text("iOS ne permet pas à VeVak de lire un SMS entrant puis d’y répondre automatiquement comme Android. Cette version garde donc l’utilisateur aux commandes : VeVak prépare le message, puis l’interface Apple vous laisse l’envoyer ou l’annuler.")
                    .font(.subheadline)
            }
            .padding(.vertical, 4)
        }
    }

    private var contactsSection: some View {
        Section("Contacts de confiance") {
            if contactStore.contacts.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    Label("Aucun contact", systemImage: "person.crop.circle.badge.plus")
                        .font(.headline)
                    Text("Ajoutez jusqu’à cinq contacts pour préparer un partage de position.")
                        .foregroundStyle(.secondary)
                }
                .padding(.vertical, 4)
            } else {
                ForEach(contactStore.contacts) { contact in
                    VStack(alignment: .leading, spacing: 10) {
                        HStack(alignment: .firstTextBaseline) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(contact.name)
                                    .font(.headline)
                                Text(contact.phoneNumber)
                                    .foregroundStyle(.secondary)
                                    .textSelection(.enabled)
                            }
                            Spacer()
                            Button(role: .destructive) {
                                contactStore.delete(id: contact.id)
                            } label: {
                                Image(systemName: "trash")
                            }
                            .accessibilityLabel("Supprimer \(contact.name)")
                        }

                        Toggle(
                            "Destinataire d’urgence",
                            isOn: Binding(
                                get: { contact.isEmergencyRecipient },
                                set: { contactStore.setEmergencyRecipient(id: contact.id, enabled: $0) }
                            )
                        )

                        Button {
                            pendingAction = .manual(contact)
                            showingConfirmation = true
                        } label: {
                            Label("Partager ma position", systemImage: "location")
                        }
                        .disabled(locationService.isRequesting)
                    }
                    .padding(.vertical, 4)
                }
            }

            if !contactStore.canAddContact {
                Text("Maximum de cinq contacts atteint.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private var emergencySection: some View {
        Section("Urgence locale") {
            Button {
                pendingAction = .emergency
                showingConfirmation = true
            } label: {
                Label("Préparer un SMS d’urgence", systemImage: "message.fill")
                    .foregroundStyle(VeVakTheme.warm)
            }
            .disabled(contactStore.emergencyRecipients.isEmpty || locationService.isRequesting)

            Text("Sur iPhone, VeVak ne peut pas envoyer ce SMS en silence : Apple affiche son composeur et vous devez toucher Envoyer. VeVak ne contacte jamais automatiquement le 112.")
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
    }

    private var privacySection: some View {
        Section("Confidentialité") {
            Label("Une seule position est demandée à chaque action.", systemImage: "location.slash")
            Label("Aucun compte VeVak, publicité ou télémétrie.", systemImage: "hand.raised")
            Label("Les contacts restent dans le stockage local de l’app.", systemImage: "iphone.gen3")

            if let persistenceError = contactStore.persistenceError {
                Text(persistenceError)
                    .foregroundStyle(.red)
            }
        }
    }

    private var errorPresentedBinding: Binding<Bool> {
        Binding(
            get: { errorMessage != nil },
            set: { if !$0 { errorMessage = nil } }
        )
    }

    private var confirmationMessage: String {
        switch pendingAction {
        case .manual(let contact):
            return "VeVak va demander votre position une seule fois puis préparer un SMS pour \(contact.name). Rien ne sera envoyé automatiquement."
        case .emergency:
            return "VeVak va demander votre position une seule fois puis préparer un SMS pour les destinataires d’urgence sélectionnés. Rien ne sera envoyé automatiquement."
        case nil:
            return ""
        }
    }

    private func requestPosition(for action: PendingAction) {
        guard MFMessageComposeViewController.canSendText() else {
            errorMessage = "Cet appareil ne peut pas présenter le composeur de messages. Cette fonction devra être testée sur un vrai iPhone."
            return
        }

        locationService.requestCurrentPosition { result in
            switch result {
            case .success(let snapshot):
                switch action {
                case .manual(let contact):
                    messageDraft = MessageDraft(
                        recipients: [contact.phoneNumber],
                        body: ShareMessageBuilder.manual(snapshot: snapshot)
                    )
                case .emergency:
                    let recipients = contactStore.emergencyRecipients.map(\.phoneNumber)
                    guard !recipients.isEmpty else {
                        errorMessage = "Aucun destinataire d’urgence n’est sélectionné."
                        return
                    }
                    messageDraft = MessageDraft(
                        recipients: recipients,
                        body: ShareMessageBuilder.emergency(snapshot: snapshot)
                    )
                }
            case .failure(let error):
                errorMessage = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
            }
        }
    }
}
