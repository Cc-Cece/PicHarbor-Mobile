import SwiftUI
import CryptoKit
import ImageIO
import UIKit

/// Optional account presentation, stored locally for offline use.
@MainActor
final class AccountAvatars: ObservableObject {
    static let shared = AccountAvatars()
    @Published private var revision = 0
    private var attempts: [String: Date] = [:]
    private var fetching = false
    private var folder: URL {
        FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("AccountAvatars", isDirectory: true)
    }
    private func url(_ account: String) -> URL {
        let hash = SHA256.hash(data: Data(account.lowercased().utf8)).map { String(format: "%02x", $0) }.joined()
        return folder.appendingPathComponent(hash + ".img")
    }
    func image(_ account: String) -> UIImage? {
        guard !account.isEmpty else { return nil }
        return UIImage(contentsOfFile: url(account).path)
    }
    func remove(_ account: String) {
        try? FileManager.default.removeItem(at: url(account)); attempts.removeValue(forKey: account); revision += 1
    }
    func refresh(_ engine: Engine, account: String) async {
        guard !account.isEmpty, engine.account == account, !fetching else { return }
        if let last = attempts[account], Date().timeIntervalSince(last) < 1800 { return }
        let file = url(account)
        if let date = (try? file.resourceValues(forKeys: [.contentModificationDateKey]))?.contentModificationDate,
           Date().timeIntervalSince(date) < 86400, image(account) != nil { return }
        attempts[account] = Date(); fetching = true
        defer { fetching = false }
        do {
            let profile = try await engine.invoke("account_profile", ["account": account])
            guard engine.account == account, profile["available"] as? Bool == true,
                  profile["account"] as? String == account,
                  let encoded = profile["avatar_base64"] as? String, encoded.utf8.count <= 700000,
                  let data = Data(base64Encoded: encoded), data.count <= 512 * 1024,
                  let source = CGImageSourceCreateWithData(data as CFData, nil),
                  let properties = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any],
                  let width = properties[kCGImagePropertyPixelWidth] as? Int,
                  let height = properties[kCGImagePropertyPixelHeight] as? Int,
                  (1...4096).contains(width), (1...4096).contains(height), UIImage(data: data) != nil else { return }
            try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
            var protectedFolder = folder
            var values = URLResourceValues(); values.isExcludedFromBackup = true
            try protectedFolder.setResourceValues(values)
            try data.write(to: file, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
            revision += 1
        } catch { /* Display metadata must never fail authentication or backup. */ }
    }
}

struct AccountAvatarView: View {
    let account: String
    var size: CGFloat = 40
    @ObservedObject private var avatars = AccountAvatars.shared
    var body: some View {
        Group {
            if let image = avatars.image(account) {
                Image(uiImage: image).resizable().scaledToFill()
            } else {
                Image(systemName: "person.crop.circle.fill").resizable().scaledToFit().foregroundStyle(.blue)
            }
        }.frame(width: size, height: size).clipShape(Circle()).accessibilityHidden(true)
    }
}
