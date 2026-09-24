import Foundation
import VeVakCrypto

@main
struct Smoke {
    static func main() throws {
        precondition(!bridgeVersion().isEmpty)
        let identity = generatePublicIdentity()
        precondition(!identity.curve25519.isEmpty)
        precondition(!identity.ed25519.isEmpty)
        let proof = try bridgeRoundTrip(plaintext: "hello from Swift")
        precondition(proof.sessionIdsMatch)
        precondition(proof.plaintext == proof.decrypted)
        print("swift-ffi-smoke=ok")
    }
}
