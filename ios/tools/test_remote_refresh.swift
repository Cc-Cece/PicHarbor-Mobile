import Foundation

@main struct RemoteRefreshTests {
    @MainActor static func main() async {
        let coordinator = RemoteRefreshCoordinator()
        let now = Date(timeIntervalSince1970: 1_800_000_000)
        var calls = 0
        var installedRevision = 1
        func fetch(_ revision: Int, success: Bool = true) async -> Bool {
            calls += 1
            if success { installedRevision = revision }
            return success
        }
        let first = await coordinator.refresh(now: now) { await fetch(2) }
        precondition(first && calls == 1 && installedRevision == 2, "Cold launch fetches before executing cached content")
        _ = await coordinator.refresh(now: now.addingTimeInterval(1)) { await fetch(3) }
        precondition(calls == 1, "Overlapping lifecycle notifications are throttled")
        _ = await coordinator.refresh(force: true, now: now.addingTimeInterval(2)) { await fetch(3) }
        precondition(calls == 2 && installedRevision == 3, "Opening More explicitly fetches a new revision")
        _ = await coordinator.refresh(now: now.addingTimeInterval(62)) { await fetch(4) }
        precondition(calls == 3 && installedRevision == 4, "Foreground heartbeat does not stay on the startup revision")
        let offline = await coordinator.refresh(force: true, now: now.addingTimeInterval(63)) { await fetch(5, success: false) }
        precondition(!offline && installedRevision == 4, "Offline failures preserve the last verified content")
        _ = await coordinator.refresh(now: now.addingTimeInterval(64)) { await fetch(5) }
        precondition(calls == 4, "A failed request has a short retry backoff")
        _ = await coordinator.refresh(now: now.addingTimeInterval(69)) { await fetch(5) }
        precondition(calls == 5 && installedRevision == 5, "A failed request is retried without restarting the app")

        let concurrent = RemoteRefreshCoordinator()
        var entered = false
        var release: CheckedContinuation<Void, Never>?
        let original = Task { @MainActor in
            await concurrent.refresh(force: true) {
                entered = true
                await withCheckedContinuation { release = $0 }
                return true
            }
        }
        while !entered { await Task.yield() }
        var extraCalls = 0
        var followerEntered = false
        let follower = Task { @MainActor in
            followerEntered = true
            return await concurrent.refresh(force: true) { extraCalls += 1; return false }
        }
        while !followerEntered { await Task.yield() }
        release?.resume()
        let result = await original.value
        let joined = await follower.value
        precondition(result && joined && extraCalls == 0, "Concurrent page and lifecycle refreshes share one fetch")
        print("PASS: 8 remote content refresh checks")
    }
}
