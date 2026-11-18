import XCTest
@testable import PicHarbor

final class AutomaticLivePhotoTests: XCTestCase {
    func testDefaultAndLegacySelections() throws {
        XCTAssertEqual(BackupSettings().live, .automatic)
        XCTAssertEqual(try JSONDecoder().decode(LiveStrategy.self, from: Data("\"applePair\"".utf8)), .applePair)
        XCTAssertEqual(try JSONDecoder().decode(LiveStrategy.self, from: Data("\"appleReconcile\"".utf8)), .appleReconcile)
    }
    func testFrozenPlanSurvivesCodingAndRejectsModifiedSources() throws {
        var resource = MediaResource(role: "original.photo", path: "stage", name: "photo.jpg", sha1: "one", sha256: "two", size: 100)
        let plan = AutomaticLivePlan(version: 1, requested: "automatic", kind: "raw_resources", strategy: "raw", warning: "LIVE_PAIR_INCOMPLETE", source_key: "key", sources: [.init(id: resource.id, size: 100, sha256: "two")], pairs: [])
        try plan.validate([resource])
        XCTAssertEqual(try JSONDecoder().decode(AutomaticLivePlan.self, from: JSONEncoder().encode(plan)), plan)
        resource.sha256 = "changed"
        XCTAssertThrowsError(try plan.validate([resource]))
        var job = BackupJob(account: "a", assetID: "asset", assetRevision: "1", settings: BackupSettings())
        job.livePlan = plan; job.state = .completed
        XCTAssertFalse(job.friendlyMessage.isEmpty)
        XCTAssertEqual(try JSONDecoder().decode(BackupJob.self, from: JSONEncoder().encode(job)).livePlan, plan)
    }
}
