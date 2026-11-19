import SwiftUI
import PhotosUI
import UniformTypeIdentifiers

struct RemoteMoreView: View {
    var action: String? = nil
    var providedPage: [String: Any]? = nil
    @EnvironmentObject private var model: HarborModel
    @ObservedObject private var remote = RemoteServices.shared
    @State private var page: [String: Any] = [:]
    @State private var values: [String: String] = [:]
    @State private var files: [RemoteAttachment] = []
    @State private var selectedImages: [PhotosPickerItem] = []
    @State private var importing = false
    @State private var generatingDiagnostic = false
    @State private var busy = false
    @State private var message = ""
    var body: some View {
        List {
            ForEach(Array((page["items"] as? [[String: Any]] ?? []).enumerated()), id: \.offset) { _, item in node(item) }
            if busy { ProgressView() }
        }
        .navigationTitle(page["title"] as? String ?? L10n.text("更多"))
        .navigationBarTitleDisplayMode(.inline)
        .task(id: remote.revision) {
            if let providedPage { page = providedPage; return }
            if !values.isEmpty || !files.isEmpty { return }
            let result = await remote.call(action == nil ? "more" : "action", action.map { ["action": $0] } ?? [:])
            page = result["page"] as? [String: Any] ?? [:]
        }
        .sheet(isPresented: $generatingDiagnostic) {
            NavigationStack {
                MobileDiagnosticsView(onAttach: { url in
                    guard !files.contains(where: { $0.kind == "diagnostic" }) else { return }
                    files.append(RemoteAttachment(path: url.path, kind: "diagnostic", mime: "application/zip"))
                })
                .toolbar { ToolbarItem(placement: .cancellationAction) { Button(L10n.text("取消")) { generatingDiagnostic = false } } }
            }.environmentObject(model)
        }
        .fileImporter(isPresented: $importing, allowedContentTypes: [.zip], allowsMultipleSelection: false) { result in
            do {
                guard let url = try result.get().first, files.filter({ $0.kind == "diagnostic" }).isEmpty else { return }
                let access = url.startAccessingSecurityScopedResource()
                defer { if access { url.stopAccessingSecurityScopedResource() } }
                let size = try url.resourceValues(forKeys: [.fileSizeKey]).fileSize ?? 0
                guard size > 0 && size <= 5 * 1024 * 1024 else { message = L10n.text("请选择 5 MiB 以内的诊断文件。"); return }
                let target = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".zip")
                try FileManager.default.copyItem(at: url, to: target)
                files.append(RemoteAttachment(path: target.path, kind: "diagnostic", mime: "application/zip"))
            } catch { message = L10n.text("无法添加此诊断文件。") }
        }
        .onChange(of: selectedImages) { _, items in
            Task {
                for item in items.prefix(3) where files.filter({ $0.kind == "screenshot" }).count < 3 {
                    guard let data = try? await item.loadTransferable(type: Data.self), data.count <= 10 * 1024 * 1024, let original = UIImage(data: data) else { continue }
                    let scale = min(1, 2048 / max(original.size.width, original.size.height))
                    let size = CGSize(width: original.size.width * scale, height: original.size.height * scale)
                    let format = UIGraphicsImageRendererFormat(); format.scale = 1
                    let image = UIGraphicsImageRenderer(size: size, format: format).image { _ in original.draw(in: CGRect(origin: .zero, size: size)) }
                    guard let jpeg = image.jpegData(compressionQuality: 0.85), jpeg.count <= 5 * 1024 * 1024 else { continue }
                    let target = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".jpg")
                    do { try jpeg.write(to: target); files.append(RemoteAttachment(path: target.path, kind: "screenshot", mime: "image/jpeg")) } catch { message = L10n.text("无法添加截图。") }
                }
                selectedImages = []
            }
        }
        .alert("PicHarbor", isPresented: Binding(get: { !message.isEmpty }, set: { if !$0 { message = "" } })) { Button(L10n.text("好")) { message = "" } } message: { Text(message) }
    }
    private func binding(_ id: String) -> Binding<String> { Binding(get: { values[id] ?? "" }, set: { values[id] = String($0.prefix(10000)) }) }
    private func node(_ item: [String: Any]) -> AnyView {
        let id = item["id"] as? String ?? "field", title = item["title"] as? String ?? ""
        switch item["type"] as? String ?? "" {
        case "group":
            let children = item["items"] as? [[String: Any]] ?? []
            return AnyView(Section(title) { ForEach(Array(children.enumerated()), id: \.offset) { _, child in node(child) } })
        case "text", "result": return AnyView(Text(item["text"] as? String ?? ""))
        case "input", "number": return AnyView(TextField(title, text: binding(id)).disabled(busy))
        case "multiline": return AnyView(TextField(title, text: binding(id), axis: .vertical).lineLimit(5...12).disabled(busy))
        case "toggle": return AnyView(Toggle(title, isOn: Binding(get: { values[id] == "true" }, set: { values[id] = String($0) })).disabled(busy))
        case "select":
            let options = item["options"] as? [[String: String]] ?? []
            return AnyView(Picker(title, selection: binding(id)) { ForEach(Array(options.enumerated()), id: \.offset) { _, option in Text(option["title"] ?? "").tag(option["value"] ?? "") } }.disabled(busy))
        case "attachments":
            return AnyView(Section(title) {
                PhotosPicker(selection: $selectedImages, maxSelectionCount: 3, matching: .images) { Label(L10n.text("添加截图"), systemImage: "photo") }.disabled(busy || files.filter { $0.kind == "screenshot" }.count >= 3)
                Button { generatingDiagnostic = true } label: { Label(L10n.text("诊断文件"), systemImage: "doc.badge.gearshape") }.disabled(busy || files.contains { $0.kind == "diagnostic" })
                Button { importing = true } label: { Label(L10n.text("从文件选择"), systemImage: "folder") }.disabled(busy || files.contains { $0.kind == "diagnostic" })
                ForEach(files) { file in HStack {
                    if file.kind == "screenshot", let image = UIImage(contentsOfFile: file.path) { Image(uiImage: image).resizable().scaledToFit().frame(width: 64, height: 64).accessibilityLabel(L10n.text("所选截图")) } else { Text(L10n.text("诊断文件")) }
                    Spacer()
                    Button(L10n.text("移除")) { files.removeAll { $0.id == file.id } }.disabled(busy)
                } }
            })
        case "button":
            if let value = item["url"] as? String, value.hasPrefix("https://"), let url = URL(string: value) { return AnyView(Link(title, destination: url)) }
            if item["capability"] as? String == "feedback.submit" {
                return AnyView(Button(title) { Task {
                    guard !busy else { return }; busy = true; defer { busy = false }
                    let result = await remote.submit(values, files: files, account: model.account)
                    if result["ok"] as? Bool == true {
                        message = result["queued"] as? Bool == true ? L10n.text("反馈已保存在本机，联网后自动发送。") : L10n.text("反馈已收到：") + (result["id"] as? String ?? "")
                        values = [:]; files = []
                    } else { message = L10n.text("请填写反馈内容，并检查附件后重试。") }
                } }.disabled(busy))
            }
            return AnyView(NavigationLink(title) { RemoteMoreView(action: item["action"] as? String ?? "") })
        case "loading": return AnyView(ProgressView())
        default: return AnyView(EmptyView())
        }
    }
}
