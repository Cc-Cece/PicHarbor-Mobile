import Foundation
import Security

enum KeychainStore {
    private static var query: [String: Any] {
        [kSecClass as String: kSecClassGenericPassword,
         kSecAttrService as String: "PicHarbor.gpmc", kSecAttrAccount as String: "credential"]
    }
    static func load(account: String? = nil) throws -> String? {
        var request = query
        if let account { request[kSecAttrAccount as String] = account }
        else {
            let marker = try sessionMarker()
            if let selected = SavedSessionPolicy.account(marker: marker,
                preferred: UserDefaults.standard.string(forKey: "activeAccount"), accounts: try accounts()) {
                request[kSecAttrAccount as String] = selected
            } else if marker != nil { return nil }
            // With no selector, retain support for the original legacy slot.
        }
        request[kSecReturnData as String] = true
        request[kSecMatchLimit as String] = kSecMatchLimitOne
        var item: CFTypeRef?
        let status = SecItemCopyMatching(request as CFDictionary, &item)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess, let data = item as? Data,
              let text = String(data: data, encoding: .utf8) else { throw HarborError("KEYCHAIN_ERROR") }
        return text
    }
    static func save(_ text: String, account: String = "credential") throws {
        var query = self.query
        query[kSecAttrAccount as String] = account
        let data = Data(text.utf8)
        let status = SecItemUpdate(query as CFDictionary, [kSecValueData as String: data] as CFDictionary)
        if status == errSecItemNotFound {
            var item = query
            item[kSecValueData as String] = data
            item[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            guard SecItemAdd(item as CFDictionary, nil) == errSecSuccess else { throw HarborError("KEYCHAIN_ERROR") }
        } else if status != errSecSuccess { throw HarborError("KEYCHAIN_ERROR") }
        if account != "credential" {
            try saveSessionMarker(account)
            UserDefaults.standard.set(account, forKey: "activeAccount")
            // New account item is durable before retiring the legacy slot.
            SecItemDelete(self.query as CFDictionary)
        }
    }
    static func accounts() throws -> [String] {
        var request = query
        request.removeValue(forKey: kSecAttrAccount as String)
        request[kSecReturnAttributes as String] = true
        request[kSecMatchLimit as String] = kSecMatchLimitAll
        var result: CFTypeRef?
        let status = SecItemCopyMatching(request as CFDictionary, &result)
        if status == errSecItemNotFound { return [] }
        guard status == errSecSuccess else { throw HarborError("KEYCHAIN_ERROR") }
        return (result as? [[String: Any]] ?? []).compactMap { $0[kSecAttrAccount as String] as? String }.filter { $0 != "credential" }.sorted()
    }
    static func clear() throws {
        var request = query
        let active = SavedSessionPolicy.account(marker: try sessionMarker(),
            preferred: UserDefaults.standard.string(forKey: "activeAccount"), accounts: try accounts())
        if let active { request[kSecAttrAccount as String] = active }
        // An empty durable marker explicitly records logout, even when other
        // saved accounts remain or ordinary preferences are lost on reinstall.
        try saveSessionMarker("")
        let status = SecItemDelete(request as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else { throw HarborError("KEYCHAIN_ERROR") }
        UserDefaults.standard.removeObject(forKey: "activeAccount")
    }

    private static var sessionQuery: [String: Any] {
        [kSecClass as String: kSecClassGenericPassword,
         kSecAttrService as String: "PicHarbor.session", kSecAttrAccount as String: "activeAccount"]
    }
    private static func sessionMarker() throws -> String? {
        var request = sessionQuery
        request[kSecReturnData as String] = true
        request[kSecMatchLimit as String] = kSecMatchLimitOne
        var item: CFTypeRef?
        let status = SecItemCopyMatching(request as CFDictionary, &item)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess, let data = item as? Data,
              let value = String(data: data, encoding: .utf8) else { throw HarborError("KEYCHAIN_ERROR") }
        return value
    }
    private static func saveSessionMarker(_ account: String) throws {
        let data = Data(account.utf8)
        let status = SecItemUpdate(sessionQuery as CFDictionary, [kSecValueData as String: data] as CFDictionary)
        if status == errSecItemNotFound {
            var item = sessionQuery
            item[kSecValueData as String] = data
            item[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
            guard SecItemAdd(item as CFDictionary, nil) == errSecSuccess else { throw HarborError("KEYCHAIN_ERROR") }
        } else if status != errSecSuccess { throw HarborError("KEYCHAIN_ERROR") }
    }
}
