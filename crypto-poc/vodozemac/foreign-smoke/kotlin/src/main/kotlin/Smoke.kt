package smoke

import org.vevak.crypto.bridgeRoundTrip
import org.vevak.crypto.bridgeVersion
import org.vevak.crypto.generatePublicIdentity

fun compileSmoke() {
    check(bridgeVersion().isNotBlank())
    val identity = generatePublicIdentity()
    check(identity.curve25519.isNotBlank())
    check(identity.ed25519.isNotBlank())
    val proof = bridgeRoundTrip("hello from Kotlin")
    check(proof.sessionIdsMatch)
    check(proof.plaintext == proof.decrypted)
}
