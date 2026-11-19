import Foundation
import SwiftUI
import CryptoKit
import UIKit
import Photos
import SelfRefreshKit

@MainActor final class RemoteServices: ObservableObject {
    static let shared = RemoteServices()
    @Published var startupUI: [String: Any]?
    @Published var hintMessage = ""
    @Published var update: [String: Any]?
    @Published var revision = 0
    @Published private(set) var forced = Set<String>()
    @Published private(set) var hints: [String: [String: Any]] = [:]
    private(set) var effective: [String: Any] = [:]
    private var policyScope = ""
    private var started = false
    private var lastFlush = Date.distantPast
    private var heartbeat: Task<Void, Never>?
    private var batchProjection: Task<Void, Never>?
    private var pendingBatches = Set<String>()
    private let queue = DispatchQueue(label: "io.picharbor.remote")
    private var home: String { FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("PicHarbor").path }
    func call(_ action: String, _ values: [String: Any] = [:]) async -> [String: Any] {
        var payload = values
        payload["context"] = ["platform": "ios", "version": Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "unknown", "build": Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "", "channel": "release", "language": L10n.language.rawValue, "system": UIDevice.current.systemVersion]
        guard let data = try? JSONSerialization.data(withJSONObject: payload) else { return [:] }
        let text = String(decoding: data, as: UTF8.self), directory = home
        return await withCheckedContinuation { continuation in
            queue.async {
                let result = PythonBridge.remote(directory, action: action, payload: text)
                let decoded = (try? JSONSerialization.jsonObject(with: Data(result.utf8))) as? [String: Any] ?? [:]
                continuation.resume(returning: decoded)
            }
        }
    }
    func start(_ model: HarborModel) async {
        guard !started, model.engine.ready else { return }
        started = true
        startupUI = await call("startup")["ui"] as? [String: Any]
        _ = await call("sync")
        model.settings = await resolve(model.settings, scope: model.account)
        _ = await model.saveSettings()
        update = await call("update")["update"] as? [String: Any]
        revision += 1
        heartbeat = Task { [weak self, weak model] in
            while !Task.isCancelled {
                guard let self, let model else { return }
                await self.snapshotFacts(model)
                if UIApplication.shared.applicationState == .active { _ = await self.call("lifecycle", ["phase": "heartbeat"]) }
                await self.flushIfNeeded()
                try? await Task.sleep(for: .seconds(60))
            }
        }
    }
    func resolve(_ settings: BackupSettings, scope: String) async -> BackupSettings {
        guard let encoded = try? JSONEncoder().encode(settings), var values = (try? JSONSerialization.jsonObject(with: encoded)) as? [String: Any] else { return settings }
        values["language"] = LanguagePreferences.shared.selection
        values["archiveEnabled"] = ActivityArchivePreferences.shared.enabled
        values["archiveDays"] = ActivityArchivePreferences.shared.days
        values["activitySource"] = UserDefaults.standard.string(forKey: "activity.source.v2") ?? "all"
        values["activityStatus"] = UserDefaults.standard.string(forKey: "activity.status.v2") ?? "all"
        values["archiveSource"] = UserDefaults.standard.string(forKey: "activity.archive.source.v1") ?? "all"
        values["pipelineMode"] = settings.pipelineMode?.rawValue ?? "automatic"
        values["pipelineOverrides"] = values["pipelineOverrides"] ?? NSNull()
        values["executionOverrides"] = values["executionOverrides"] ?? NSNull()
        let result = await call("resolve", ["scope": scope, "values": values])
        guard result["ok"] as? Bool == true, let effective = result["values"] as? [String: Any] else { return settings }
        self.effective = effective
        forced = Set(result["forced"] as? [String] ?? [])
        hints = result["hints"] as? [String: [String: Any]] ?? [:]
        policyScope = result["scope"] as? String ?? ""
        for (key, value) in effective { values[key] = value }
        for (key, stored) in ["activitySource": "activity.source.v2", "activityStatus": "activity.status.v2", "archiveSource": "activity.archive.source.v1"] {
            if let value = effective[key] as? String { UserDefaults.standard.set(value, forKey: stored) }
        }
        if let language = effective["language"] as? String, AppLanguage(rawValue: language) != nil, language != LanguagePreferences.shared.selection { LanguagePreferences.shared.selection = language }
        if let enabled = effective["archiveEnabled"] as? Bool, enabled != ActivityArchivePreferences.shared.enabled { ActivityArchivePreferences.shared.enabled = enabled }
        if let days = effective["archiveDays"] as? Int, days != ActivityArchivePreferences.shared.days { ActivityArchivePreferences.shared.setDays(String(days)) }
        guard let data = try? JSONSerialization.data(withJSONObject: values), let resolved = try? JSONDecoder().decode(BackupSettings.self, from: data) else { return settings }
        return resolved
    }
    func intercept(_ key: String) {
        guard let hint = hints.removeValue(forKey: key) else { return }
        hintMessage = hint["message"] as? String ?? L10n.text("此设置已由应用配置调整。")
        let scope = hint["scope"] as? String ?? policyScope, receipt = hint["id"] as? String ?? ""
        Task { _ = await call("hint", ["scope": scope, "receipt": receipt]) }
    }
    func event(_ name: String, id: String = "", duration: Double? = nil, bytes: Int64? = nil) {
        Task { var payload: [String: Any] = ["name": name, "id": id]; if let duration { payload["duration_ms"] = duration }; if let bytes { payload["bytes"] = bytes }; _ = await call("event", payload) }
    }
    func observe(_ name: String, fields: [String: Any] = [:], id: String = "") {
        Task { _ = await call("observe", ["name": name, "fields": fields, "id": id]) }
    }
    func lifecycle(_ phase: String) {
        guard started else { return }
        Task { _ = await call("lifecycle", ["phase": phase]) }
    }
    private func snapshotFacts(_ model: HarborModel) async {
        let authorization = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        let access = authorization == .authorized ? "full" : authorization == .limited ? "limited" : "denied"
        let memory = ProcessInfo.processInfo.physicalMemory
        _ = await call("observe", ["name": "environment.snapshot", "fields": ["os_version": UIDevice.current.systemVersion, "model_family": UIDevice.current.model, "ram_bucket": memory < 3 * 1024 * 1024 * 1024 ? "under3gb" : memory < 6 * 1024 * 1024 * 1024 ? "3to6gb" : "6gbplus", "photo_access": access, "power_save": ProcessInfo.processInfo.isLowPowerModeEnabled, "auth_state": model.account.isEmpty ? "signed_out" : "selected", "network": model.network.connected ? "connected" : "offline"]])
        await renewalFacts()
        _ = await call("observe", ["name": "preferences.snapshot", "fields": ["auto_backup": model.settings.automatic, "wifi_only": model.settings.wifiOnly, "quality": model.settings.quality.rawValue, "live_mode": model.settings.live.rawValue, "concurrency_mode": model.settings.pipelineMode?.rawValue ?? "automatic"]])
        _ = await call("observe", ["name": "backup.conditions", "fields": ["blocked_reason": model.account.isEmpty ? "signed_out" : !model.settings.automatic ? "auto_off" : access == "denied" ? "photo_permission" : !model.network.connected ? "offline" : model.settings.wifiOnly && !model.network.wifi ? "wifi_required" : "ready"]])
    }
    private func renewalFacts() async {
        let manager = SelfRefreshManager.shared
        var fields: [String: Any] = ["operation": "status", "renewal_configured": manager.isLocalCertificateTakenOver || manager.hasSuccessfulProfileRefresh]
        if let expiration = manager.profileMetadata?.expirationDate { fields["validity_hours"] = max(0, min(8760, Int(expiration.timeIntervalSinceNow / 3600))) }
        _ = await call("observe", ["name": "renewal.stage", "fields": fields])
    }
    func projectBatch(_ value: BackupJob, model: HarborModel) {
        pendingBatches.insert(value.batchID ?? value.id)
        guard batchProjection == nil else { return }
        batchProjection = Task { [weak self, weak model] in
            try? await Task.sleep(for: .seconds(3))
            guard let self, let model else { return }
            let ids = self.pendingBatches; self.pendingBatches.removeAll(); self.batchProjection = nil
            let groups = Dictionary(grouping: model.jobs.filter { ids.contains($0.batchID ?? $0.id) }, by: { $0.batchID ?? $0.id })
            for (id, jobs) in groups {
                let finished = jobs.allSatisfy { [.completed, .cancelled, .failed, .attention].contains($0.state) }
                let failed = jobs.filter { $0.state == .failed || $0.state == .attention }.count
                let status = jobs.allSatisfy { $0.state == .cancelled } ? "cancelled" : finished ? (failed > 0 ? "failed" : "completed") : jobs.contains { $0.state.active } ? "active" : "waiting"
                var fields: [String: Any] = ["status": status, "source": jobs.first?.origin?.rawValue ?? "manual", "media_total": jobs.count, "media_success": jobs.filter { $0.state == .completed }.count, "media_failed": failed, "resource_total": jobs.reduce(0) { $0 + $1.resources.count }, "bytes": jobs.filter { $0.state == .completed }.reduce(Int64(0)) { total, job in total + job.resources.reduce(Int64(0)) { $0 + $1.size } }, "retries": jobs.reduce(0) { $0 + max(0, $1.attempts - 1) }]
                for (metric, stages) in [("prepare_ms", ["preparation"]), ("upload_ms", ["upload"]), ("confirm_ms", ["commit", "confirmation"])] {
                    let measured = jobs.flatMap { job in stages.compactMap { job.pipelineSeconds?[$0] } }
                    if !measured.isEmpty { fields[metric] = measured.reduce(0, +) * 1000 }
                }
                _ = await self.call("observe", ["name": "backup.batch", "id": id, "fields": fields])
            }
        }
    }
    func flushIfNeeded() async {
        guard Date().timeIntervalSince(lastFlush) > 60, HarborModel.shared.engine.ready else { return }
        lastFlush = Date()
        _ = await call("flush")
    }
    func submit(_ values: [String: String], files: [RemoteAttachment], account: String) async -> [String: Any] {
        var payload = values
        payload["email"] = account
        payload["account_ref"] = SHA256.hash(data: Data((home + account).utf8)).map { String(format: "%02x", $0) }.joined()
        return await call("feedback", ["values": payload, "files": files.map { ["path": $0.path, "kind": $0.kind, "mime": $0.mime] }])
    }
}
struct RemoteAttachment: Identifiable {
    let id = UUID()
    let path: String
    let kind: String
    let mime: String
}
private struct RemoteSettingGuard: ViewModifier {
    let key: String
    @ObservedObject private var remote = RemoteServices.shared
    func body(content: Content) -> some View {
        content.overlay {
            if remote.forced.contains(key) || remote.hints[key] != nil {
                Color.clear.contentShape(Rectangle()).onTapGesture { remote.intercept(key) }
                    .accessibilityElement().accessibilityLabel(L10n.text("设置"))
                    .accessibilityAddTraits(.isButton).accessibilityAction { remote.intercept(key) }
            }
        }
    }
}
extension View {
    func remoteSetting(_ key: String) -> some View { modifier(RemoteSettingGuard(key: key)) }
}
struct RemoteAlertHost: View {
    @EnvironmentObject private var model: HarborModel
    @ObservedObject private var remote = RemoteServices.shared
    var body: some View {
        Color.clear.frame(width: 0, height: 0)
            .alert(remote.startupUI?["title"] as? String ?? "PicHarbor", isPresented: Binding(get: { (remote.startupUI != nil && remote.startupUI?["items"] == nil) || !remote.hintMessage.isEmpty }, set: { if !$0 { remote.startupUI = nil; remote.hintMessage = "" } })) {
                Button(L10n.text("好")) { remote.startupUI = nil; remote.hintMessage = "" }
            } message: { Text(remote.hintMessage.isEmpty ? remote.startupUI?["message"] as? String ?? "" : remote.hintMessage) }
            .sheet(isPresented: Binding(get: { remote.startupUI?["items"] != nil }, set: { if !$0 { remote.startupUI = nil } })) {
                NavigationStack { RemoteMoreView(providedPage: remote.startupUI).toolbar { ToolbarItem(placement: .topBarTrailing) { Button(L10n.text("关闭")) { remote.startupUI = nil } } } }.environmentObject(model)
            }
    }
}
