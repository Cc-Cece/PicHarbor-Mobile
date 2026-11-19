import Foundation

/// Coalesces lifecycle, connectivity and page requests without caching failures forever.
@MainActor final class RemoteRefreshCoordinator {
    private var pending: Task<Bool, Never>?
    private var lastAttempt: Date?
    private var lastSucceeded = false

    func refresh(force: Bool = false, now: Date = Date(), operation: @escaping @MainActor () async -> Bool) async -> Bool {
        if let pending { return await pending.value }
        if !force, let lastAttempt, now.timeIntervalSince(lastAttempt) < (lastSucceeded ? 30 : 5) {
            return lastSucceeded
        }
        lastAttempt = now
        let task = Task { await operation() }
        pending = task
        let succeeded = await task.value
        lastSucceeded = succeeded
        pending = nil
        return succeeded
    }
}
