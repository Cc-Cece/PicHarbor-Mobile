import Foundation
import Security

struct HarborError: Error { init(_ code: String) {} }

@main
struct SavedSessionTests {
    static func main() throws {
        // These integration cases use only an ephemeral GitHub runner's keychain.
        guard ProcessInfo.processInfo.environment["GITHUB_ACTIONS"] == "true" else {
            fatalError("Run on an isolated CI runner, never a user's keychain")
        }
        func reset() {
            for service in ["PicHarbor.gpmc", "PicHarbor.session"] {
                SecItemDelete([kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service] as CFDictionary)
            }
            UserDefaults.standard.removeObject(forKey: "activeAccount")
        }
        func selected(_ marker: String?, _ preferred: String?, _ accounts: [String]) -> String? {
            SavedSessionPolicy.account(marker: marker, preferred: preferred, accounts: accounts)
        }
        precondition(selected(nil, nil, ["a"]) == "a")
        precondition(selected(nil, "missing", ["a"]) == "a")
        precondition(selected(nil, nil, ["a", "b"]) == nil)
        precondition(selected(nil, "b", ["a", "b"]) == "b")
        precondition(selected("a", "b", ["a", "b"]) == "a")
        precondition(selected("", "a", ["a"]) == nil)
        precondition(selected("missing", "a", ["a"]) == nil)
        precondition(selected(nil, nil, []) == nil)
        for code in ["NETWORK_ERROR", "KEYCHAIN_ERROR", "PROXY_START_FAILED", "PROXY_UNAVAILABLE", "PROXY_CONFIG_REQUIRED"] {
            precondition(SavedSessionPolicy.retryable(code))
        }
        for code in ["LOGIN_REQUIRED", "INVALID_CREDENTIAL", "ENGINE_ERROR"] {
            precondition(!SavedSessionPolicy.retryable(code))
        }
        reset(); defer { reset() }
        try KeychainStore.save("alice-secret", account: "alice@example.test")
        try KeychainStore.save("bob-secret", account: "bob@example.test")
        UserDefaults.standard.removeObject(forKey: "activeAccount")
        let bob = try KeychainStore.load()
        precondition(bob == "bob-secret", "Container reset must retain the last selected account")
        try KeychainStore.clear()
        let signedOut = try KeychainStore.load()
        let remaining = try KeychainStore.accounts()
        precondition(signedOut == nil && remaining == ["alice@example.test"], "Logout must not select another saved account")
        let alice = try KeychainStore.load(account: "alice@example.test")
        precondition(alice == "alice-secret", "Explicit account selection remains possible")
        try KeychainStore.save(alice!, account: "alice@example.test")
        UserDefaults.standard.removeObject(forKey: "activeAccount")
        let recovered = try KeychainStore.load()
        precondition(recovered == alice)
        // Pre-fix installs have named credentials but no durable selector.
        SecItemDelete([kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: "PicHarbor.session"] as CFDictionary)
        let migrated = try KeychainStore.load()
        precondition(migrated == alice, "One legacy saved account can be recovered safely")
        try KeychainStore.save("bob-secret", account: "bob@example.test")
        SecItemDelete([kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: "PicHarbor.session"] as CFDictionary)
        UserDefaults.standard.removeObject(forKey: "activeAccount")
        let ambiguous = try KeychainStore.load()
        precondition(ambiguous == nil, "Missing selection must not guess between accounts")
        reset()
        try KeychainStore.save("legacy-secret")
        let legacy = try KeychainStore.load()
        precondition(legacy == "legacy-secret", "Original credential slot still migrates")
        print("PASS: 23 saved-session selection, recovery, logout and Keychain checks")
    }
}
