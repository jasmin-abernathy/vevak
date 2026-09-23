import CoreLocation
import Foundation
import Combine

@MainActor
final class LocationService: NSObject, ObservableObject, CLLocationManagerDelegate {
    enum LocationError: LocalizedError {
        case busy
        case permissionDenied
        case unavailable

        var errorDescription: String? {
            switch self {
            case .busy:
                return "Une demande de position est déjà en cours."
            case .permissionDenied:
                return "L’accès à la position est refusé. Vous pouvez le réactiver dans Réglages."
            case .unavailable:
                return "Aucune position exploitable n’a été obtenue."
            }
        }
    }

    @Published private(set) var authorizationStatus: CLAuthorizationStatus
    @Published private(set) var isRequesting = false

    private let manager: CLLocationManager
    private var completion: ((Result<PositionSnapshot, Error>) -> Void)?

    override init() {
        let manager = CLLocationManager()
        self.manager = manager
        self.authorizationStatus = manager.authorizationStatus
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyNearestTenMeters
    }

    func requestCurrentPosition(
        completion: @escaping (Result<PositionSnapshot, Error>) -> Void
    ) {
        guard self.completion == nil else {
            completion(.failure(LocationError.busy))
            return
        }

        self.completion = completion
        isRequesting = true

        switch manager.authorizationStatus {
        case .authorizedAlways, .authorizedWhenInUse:
            manager.requestLocation()
        case .notDetermined:
            manager.requestWhenInUseAuthorization()
        case .denied, .restricted:
            finish(.failure(LocationError.permissionDenied))
        @unknown default:
            finish(.failure(LocationError.unavailable))
        }
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        authorizationStatus = manager.authorizationStatus
        guard completion != nil else { return }

        switch manager.authorizationStatus {
        case .authorizedAlways, .authorizedWhenInUse:
            manager.requestLocation()
        case .denied, .restricted:
            finish(.failure(LocationError.permissionDenied))
        case .notDetermined:
            break
        @unknown default:
            finish(.failure(LocationError.unavailable))
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations
            .filter({ $0.horizontalAccuracy >= 0 })
            .max(by: { $0.timestamp < $1.timestamp })
        else {
            finish(.failure(LocationError.unavailable))
            return
        }

        let snapshot = PositionSnapshot(
            latitude: location.coordinate.latitude,
            longitude: location.coordinate.longitude,
            horizontalAccuracyMeters: location.horizontalAccuracy,
            timestamp: location.timestamp
        )
        finish(.success(snapshot))
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        finish(.failure(error))
    }

    private func finish(_ result: Result<PositionSnapshot, Error>) {
        let callback = completion
        completion = nil
        isRequesting = false
        callback?(result)
    }
}
