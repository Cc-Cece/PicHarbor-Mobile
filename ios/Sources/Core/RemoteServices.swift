import Foundation
import SwiftUI
import CryptoKit
import UIKit

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
    private let queue = DispatchQueue(label: "io.picharbor.remote")
    private var home: String { FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("PicHarbor").path }
    func call(_ action: String, _ values: [String: Any] = [:]) async -> [String: Any] {
        var payload = values
        payload["context"] = ["platform": "ios", "version": Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "unknown", "language": L10n.language.rawValue, "system": UIDevice.current.systemVersion]
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
    }
    func resolve(_ settings: BackupSettings, scope: String) async -> BackupSettings {
        guard let encoded = try? JSONEncoder().encode(settings), var values = (try? JSONSerialization.jsonObject(with: encoded)) as? [String: Any] else { return settings }
        values["language"] = LanguagePreferences.shared.selection
        values["archiveEnabled"] = ActivityArchivePreferences.shared.enabled
        values["archiveDays"] = ActivityArchivePreferences.shared.days
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
    func event(_ name: String, id: String = "") { Task { _ = await call("event", ["name": name, "id": id]) } }
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
    @ObservedObject private var remote = RemoteServices.shared
    var body: some View {
        Color.clear.frame(width: 0, height: 0)
            .alert(remote.startupUI?["title"] as? String ?? "PicHarbor", isPresented: Binding(get: { remote.startupUI != nil || !remote.hintMessage.isEmpty }, set: { if !$0 { remote.startupUI = nil; remote.hintMessage = "" } })) {
                Button(L10n.text("好")) { remote.startupUI = nil; remote.hintMessage = "" }
            } message: { Text(remote.hintMessage.isEmpty ? remote.startupUI?["message"] as? String ?? "" : remote.hintMessage) }
    }
}
