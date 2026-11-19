import Foundation

/// Authentication may need the app proxy, whose route selector needs the
/// shared business core. Keep that dependency explicit at the launch boundary.
enum SessionStartup {
    @MainActor
    @discardableResult
    static func restore(prepareRouting: () async -> Bool,
                        authenticate: () async -> Bool) async -> Bool {
        guard await prepareRouting() else { return false }
        return await authenticate()
    }
}
