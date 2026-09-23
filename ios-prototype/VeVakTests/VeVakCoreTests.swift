import XCTest
@testable import VeVak

final class VeVakCoreTests: XCTestCase {
    func testTrustedContactLimitIsFive() {
        XCTAssertTrue(TrustedContactPolicy.canAdd(name: "A", phoneNumber: "+331", currentCount: 4))
        XCTAssertFalse(TrustedContactPolicy.canAdd(name: "A", phoneNumber: "+331", currentCount: 5))
    }

    func testEmptyContactFieldsAreRejected() {
        XCTAssertFalse(TrustedContactPolicy.canAdd(name: "   ", phoneNumber: "+331", currentCount: 0))
        XCTAssertFalse(TrustedContactPolicy.canAdd(name: "A", phoneNumber: "   ", currentCount: 0))
    }

    func testMapLinkUsesStablePosixFormatting() {
        let snapshot = PositionSnapshot(
            latitude: 49.1193,
            longitude: 6.1757,
            horizontalAccuracyMeters: 14.6,
            timestamp: Date(timeIntervalSince1970: 0)
        )

        XCTAssertEqual(
            ShareMessageBuilder.mapLink(snapshot: snapshot),
            "https://www.openstreetmap.org/?mlat=49.119300&mlon=6.175700#map=17/49.119300/6.175700"
        )
    }

    func testEmergencyMessageDoesNotClaimDeliveryOrEmergencyCall() {
        let snapshot = PositionSnapshot(
            latitude: 49.0,
            longitude: 6.0,
            horizontalAccuracyMeters: 20,
            timestamp: Date(timeIntervalSince1970: 0)
        )

        let message = ShareMessageBuilder.emergency(snapshot: snapshot)
        XCTAssertTrue(message.contains("n’est pas une preuve de livraison"))
        XCTAssertTrue(message.contains("ne contacte pas les secours"))
    }
}
