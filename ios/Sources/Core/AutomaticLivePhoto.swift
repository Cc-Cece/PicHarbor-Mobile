import Foundation

struct AutomaticLivePlan: Codable, Equatable {
    let version: Int
    let requested: String
    let kind: String
    let strategy: String
    let warning: String
    let source_key: String
    let sources: [Source]
    let pairs: [Pair]
    struct Source: Codable, Equatable { let id: String; let size: Int64; let sha256: String? }
    struct Pair: Codable, Equatable { let photo: String; let video: String }
    var title: String {
        switch kind {
        case "apple_pair": return L10n.text("自动 · 照片与视频配对")
        case "motion_photo": return L10n.text("自动 · 动态照片原文件上传")
        default: return L10n.text("自动 · 原文件上传")
        }
    }
    var warningText: String {
        switch warning {
        case "LIVE_PAIR_INCOMPLETE": return L10n.text("已保留可读取的原文件，但照片或动态视频不完整，未确认完整的实况效果。")
        case "LIVE_PAIR_AMBIGUOUS": return L10n.text("无法唯一确认照片与视频的配对关系，已分别保留原文件。")
        case "MOTION_STRUCTURE_UNKNOWN": return L10n.text("动态照片结构无法确认，已保留原文件，未确认动态部分。")
        case "LIVE_MIXED_REPRESENTATIONS": return L10n.text("不同版本包含不同的动态信息，已分别保留原文件。")
        default: return ""
        }
    }
    func validate(_ resources: [MediaResource]) throws {
        guard version == 1, requested == "automatic", sources.count == resources.count,
              sources.allSatisfy({ source in resources.contains { $0.id == source.id && $0.size == source.size && $0.sha256 == source.sha256 } }) else { throw HarborError("SOURCE_CHANGED") }
    }
}

extension HarborModel {
    func automaticLivePlan(for job: BackupJob) async throws -> AutomaticLivePlan {
        if let frozen = job.livePlan { try frozen.validate(job.sources); return frozen }
        let resources: [[String: Any]] = job.sources.map { resource in
            ["id": resource.id, "size": resource.size, "sha256": resource.sha256, "path": resource.path,
             "video": resource.role.hasSuffix(".video") || resource.role.hasSuffix(".pairedVideo"),
             "group": String(resource.role.split(separator: ".").first ?? "original")]
        }
        let response = try await engine.invoke("plan_live", ["account": job.account, "strategy": "automatic", "system_pair": job.isLive, "resources": resources])
        let plan = try JSONDecoder().decode(AutomaticLivePlan.self, from: JSONSerialization.data(withJSONObject: response))
        try plan.validate(job.sources)
        return plan
    }
}
