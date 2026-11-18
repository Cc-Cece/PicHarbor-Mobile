import Foundation

/// Successful profile installation is also evidence of a working on-device
/// refresh setup. It does not claim that the IPA was signed with our keychain key.
enum RefreshSetupEvidence {
    static func confirmsLocalRefresh(
        hostBundleID: String, profileBundleID: String, profileUUID: String,
        teamID: String, creationDate: Date, expirationDate: Date,
        installedAt: Date?, now: Date = Date()
    ) -> Bool {
        guard let installedAt, !hostBundleID.isEmpty, profileBundleID == hostBundleID,
              !teamID.isEmpty, !profileUUID.isEmpty, profileUUID != "CACHED-PROFILE",
              installedAt > Date(timeIntervalSince1970: 0),
              installedAt <= now.addingTimeInterval(300),
              creationDate <= installedAt.addingTimeInterval(300),
              expirationDate > installedAt else { return false }
        // Setup remains complete if the previously installed profile later expires.
        // Expiration and current tunnel connectivity are separate health states.
        return true
    }
}
