import Foundation

/// Account identity selection is separate from online authentication. Never
/// guess between accounts, or undo an explicit logout after a container reset.
enum SavedSessionPolicy {
    static func account(marker: String?, preferred: String?, accounts: [String]) -> String? {
        if let marker { return accounts.contains(marker) ? marker : nil }
        if let preferred, accounts.contains(preferred) { return preferred }
        return accounts.count == 1 ? accounts.first : nil
    }

    static func retryable(_ code: String) -> Bool {
        ["NETWORK_ERROR", "KEYCHAIN_ERROR", "PROXY_START_FAILED", "PROXY_UNAVAILABLE",
         "PROXY_CONFIG_REQUIRED", "PROXY_TEST_FAILED", "WAITING_NETWORK"].contains(code)
    }
}
