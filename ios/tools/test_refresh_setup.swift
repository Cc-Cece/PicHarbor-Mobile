import Foundation

@main
struct RefreshSetupTests {
    static func main() {
        let now = Date(timeIntervalSince1970: 1_800_000_000)
        func accepted(host: String = "app.test", bundle: String = "app.test", uuid: String = "installed-uuid", team: String = "TEAM", installed: Date? = Date(timeIntervalSince1970: 1_800_000_000), created: Date = Date(timeIntervalSince1970: 1_799_999_940), expires: Date = Date(timeIntervalSince1970: 1_800_604_800), clock: Date = Date(timeIntervalSince1970: 1_800_000_000)) -> Bool {
            RefreshSetupEvidence.confirmsLocalRefresh(hostBundleID: host, profileBundleID: bundle,
                profileUUID: uuid, teamID: team, creationDate: created, expirationDate: expires,
                installedAt: installed, now: clock)
        }
        precondition(accepted(), "A successful shortcut installation completes setup")
        precondition(!accepted(installed: nil), "A countdown alone is not installation evidence")
        precondition(!accepted(bundle: "another.app"), "Another app's profile is not evidence")
        precondition(!accepted(uuid: "CACHED-PROFILE"), "A synthetic expiration cache is not evidence")
        precondition(!accepted(uuid: ""), "Missing profile identity cannot complete setup")
        precondition(!accepted(team: ""), "Missing team identity cannot complete setup")
        precondition(!accepted(expires: now.addingTimeInterval(-1)), "Failed or expired installation cannot complete setup")
        precondition(!accepted(created: now.addingTimeInterval(600)), "Future profile cannot prove past installation")
        precondition(!accepted(installed: now.addingTimeInterval(600)), "Future success timestamp is invalid")
        precondition(accepted(clock: now.addingTimeInterval(700000)), "Later expiration does not undo completed setup")
        precondition(!accepted(host: ""), "An unknown host cannot recover setup")
        print("PASS: 11 self-refresh setup evidence checks")
    }
}
