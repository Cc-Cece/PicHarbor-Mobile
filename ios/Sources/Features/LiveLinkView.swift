import SwiftUI
import Photos
import PhotosUI
import AVFoundation

struct LiveLinkView: View {
    @ObservedObject private var language = LanguagePreferences.shared
    @EnvironmentObject private var model: HarborModel
    @ObservedObject private var link = LiveLinkController.shared
    @State private var scan = false
    @State private var manual = false
    @State private var scopePicker = false
    @State private var forget = false
    @State private var host = ""
    @State private var port = ""
    @State private var fingerprint = ""
    @State private var code = ""
    @State private var scanned = ""
    @State private var scannedHost = ""
    @State private var altHosts: [String] = []
    @State private var filling = false
    @State private var selectedImage: PhotosPickerItem?
    @State private var readingImage = false
    @State private var all = true
    @State private var albums = Set<String>()
    @State private var initializedScope = false
    @FocusState private var focused: String?

    private var assets: [PHAsset] {
        if all { return model.library.assets }
        if albums.isEmpty { return [] }
        var draft = model.settings; draft.selectedAlbums = Array(albums)
        return model.library.scope(draft)
    }
    private var ready: Bool {
        let h = host.trimmingCharacters(in: .whitespacesAndNewlines)
        let pin = fingerprint.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard LiveLinkCheck.isIPv4(h), LiveLinkCheck.port(port) != nil,
              (pin.count == 16 || pin.count == 64), LiveLinkCheck.isHex(pin) else { return false }
        if !code.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return LiveLinkCheck.pairingCode(code) != nil }
        return link.saved.map { $0.host == h && String($0.port) == port && $0.fingerprint.hasPrefix(pin) } ?? false
    }
    var body: some View {
        Form {
            if let saved = link.saved {
                Section(L10n.text("已保存的电脑")) {
                    LabeledContent(L10n.text("电脑"), value: saved.name)
                    LabeledContent(L10n.text("地址"), value: "\(saved.host):\(saved.port)")
                    Button(L10n.text("使用已保存的电脑")) {
                        host = saved.host; port = String(saved.port); fingerprint = saved.fingerprint
                        scanned = saved.fingerprint; scannedHost = saved.host; altHosts = []; code = ""; manual = false
                    }.disabled(link.busy)
                    Button(L10n.text("忘记这台电脑"), role: .destructive) { forget = true }.disabled(link.busy)
                }
            }
            Section {
                VStack(alignment: .leading, spacing: 14) {
                    Text(L10n.text("打开电脑版的接收页面，让手机和电脑连接同一局域网。"))
                        .font(.subheadline).foregroundStyle(.secondary)
                    Button { focused = nil; scan = true } label: {
                        Label(L10n.text("扫描电脑二维码"), systemImage: "qrcode.viewfinder").frame(maxWidth: .infinity, minHeight: 32)
                    }.buttonStyle(.borderedProminent).disabled(link.busy || readingImage)
                    PhotosPicker(selection: $selectedImage, matching: .images) {
                        Label(L10n.text("从图片识别"), systemImage: "photo").frame(maxWidth: .infinity, minHeight: 32)
                    }.buttonStyle(.bordered).disabled(link.busy || readingImage)
                    if readingImage { ProgressView(L10n.text("正在识别二维码")) }
                }.padding(.vertical, 4)
                if ready {
                    LabeledContent(L10n.text("目标电脑"), value: "\(host):\(port)")
                    Text(L10n.text("连接信息已就绪，点击开始发送后等待电脑确认。"))
                        .font(.footnote).foregroundStyle(.secondary)
                }
                DisclosureGroup(L10n.text("手动输入连接信息"), isExpanded: $manual) {
                    TextField(L10n.text("地址"), text: $host).keyboardType(.numbersAndPunctuation).focused($focused, equals: "host")
                    TextField(L10n.text("端口"), text: $port).keyboardType(.numberPad).focused($focused, equals: "port")
                    TextField(L10n.text("证书前 16 位或完整指纹"), text: $fingerprint).keyboardType(.asciiCapable).focused($focused, equals: "pin")
                    TextField(L10n.text("配对码"), text: $code).keyboardType(.numberPad).focused($focused, equals: "code")
                    Text(L10n.text("连接信息可在电脑版的接收页面查看。已配对的电脑无需再次填写配对码。"))
                        .font(.footnote).foregroundStyle(.secondary)
                }.textInputAutocapitalization(.never).autocorrectionDisabled().disabled(link.busy)
            } header: { Text(L10n.text("连接电脑")) }
            Section {
                LabeledContent(L10n.text("将发送"), value: L10n.text("{0} 项", [String(assets.count)]))
                Button { scopePicker = true } label: {
                    HStack { Text(L10n.text("选择照片与视频")); Spacer(); Image(systemName: "chevron.down").font(.caption) }
                }.disabled(link.busy)
                Toggle(L10n.text("允许从 iCloud 下载"), isOn: $link.allowCloud).disabled(link.busy)
                if model.library.status != .authorized && model.library.status != .limited {
                    Button(L10n.text("允许访问相册")) { Task { await model.library.authorize() } }
                }
                if link.skipped > 0 {
                    Text(L10n.text("已跳过 {0} 项仅保存在 iCloud 的原件", [String(link.skipped)]))
                        .font(.footnote).foregroundStyle(.secondary)
                }
            } header: { Text(L10n.text("发送范围")) } footer: {
                Label(L10n.text("仅用于本次传输，不改变自动备份。无需 Google 账号。"), systemImage: "info.circle")
            }
            if link.busy || link.started || !link.message.isEmpty {
                Section(L10n.text("传输状态")) {
                    if link.busy { ProgressView(value: link.fraction) { Text(link.status) } }
                    else if !link.status.isEmpty { Text(link.status) }
                    if !link.currentFile.isEmpty { Text(link.currentFile).font(.caption).lineLimit(1).truncationMode(.middle) }
                    if link.started {
                        Text(L10n.text("已发送 {0}，电脑已有 {1}，跳过 {2}。", [String(link.sentCount), String(link.hadCount), String(link.skipped)]))
                            .font(.footnote).foregroundStyle(.secondary)
                        if link.failedCount > 0 { Text(L10n.text("未能发送 {0} 项。", [String(link.failedCount)])).font(.footnote) }
                    }
                    if !link.message.isEmpty { Text(link.message).font(.footnote) }
                }
            }
            Section {
                if link.busy {
                    Button(L10n.text("停止发送"), role: .cancel) { link.stop() }.frame(maxWidth: .infinity)
                } else {
                    Button { focused = nil; start() } label: {
                        Label(L10n.text("开始发送"), systemImage: "paperplane.fill").frame(maxWidth: .infinity, minHeight: 32)
                    }.buttonStyle(.borderedProminent).disabled(!ready || assets.isEmpty || readingImage).accessibilityIdentifier("link.send")
                    if !ready { Text(L10n.text("请先扫码或填写连接信息。")).font(.footnote).foregroundStyle(.secondary) }
                }
            } footer: {
                Label(L10n.text("仅在局域网内直接传输。发送前需电脑确认；请保持应用在前台，锁屏后传输停止。"), systemImage: "info.circle")
            }
        }
        .navigationTitle(L10n.text("传输到电脑")).navigationBarTitleDisplayMode(.inline)
        .scrollDismissesKeyboard(.interactively)
        .toolbar { ToolbarItemGroup(placement: .keyboard) { Spacer(); Button(L10n.text("完成")) { focused = nil } } }
        .onAppear {
            link.reload()
            if !initializedScope { albums = Set(model.settings.selectedAlbums); all = albums.isEmpty; initializedScope = true }
        }
        .onDisappear { if !scan { link.stop() } }
        .onChange(of: fingerprint) { _, value in
            guard !filling else { return }
            let prefix = value.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
            if !scanned.isEmpty, !scanned.lowercased().hasPrefix(prefix) { scanned = "" }
        }
        .sheet(isPresented: $scan) { LiveLinkScanner(onCode: fill) }
        .sheet(isPresented: $scopePicker) {
            NavigationStack {
                Form {
                    Section {
                        Toggle(L10n.text("全部可访问照片与视频"), isOn: $all)
                        ForEach(model.library.albums) { album in
                            Button {
                                if all { albums = []; all = false }
                                if albums.contains(album.id) { albums.remove(album.id) } else { albums.insert(album.id) }
                            } label: {
                                HStack { Text(album.title).foregroundStyle(.primary); Spacer(); Text(album.count.formatted()).foregroundStyle(.secondary); if !all && albums.contains(album.id) { Image(systemName: "checkmark") } }
                            }
                        }
                    } footer: { Label(L10n.text("仅用于本次传输，不改变自动备份。无需 Google 账号。"), systemImage: "info.circle") }
                }.navigationTitle(L10n.text("发送范围")).navigationBarTitleDisplayMode(.inline)
                    .toolbar { ToolbarItem(placement: .confirmationAction) { Button(L10n.text("完成")) { scopePicker = false } } }
            }.presentationDetents([.medium, .large]).presentationDragIndicator(.visible)
        }
        .confirmationDialog(L10n.text("忘记这台电脑？"), isPresented: $forget, titleVisibility: .visible) {
            Button(L10n.text("忘记这台电脑"), role: .destructive) {
                if let saved = link.saved, host == saved.host, port == String(saved.port) { host = ""; port = ""; fingerprint = ""; code = ""; scanned = "" }
                link.forget()
            }
        } message: { Text(L10n.text("下次连接需要重新扫码配对，已发送的文件会保留。")) }
        .onChange(of: selectedImage) { _, image in
            guard let image else { return }
            readingImage = true
            Task { @MainActor in
                defer { readingImage = false; selectedImage = nil }
                do {
                    guard let data = try await image.loadTransferable(type: Data.self) else { throw HarborError("QR_IMAGE_UNAVAILABLE") }
                    let values = try await Task.detached(priority: .userInitiated) { try SubscriptionQRCode.messages(in: data) }.value
                    guard let target = values.compactMap(LiveLinkCheck.parseQR).first else { throw LiveLinkFailure.qr }
                    fill(target)
                } catch { link.message = (error as? LocalizedError)?.errorDescription ?? L10n.text("未能识别图片，请换一张清晰的二维码图片。") }
            }
        }
    }
    private func fill(_ target: LiveLinkTarget) {
        filling = true; host = target.host; port = String(target.port); code = target.code
        scannedHost = target.host; altHosts = target.alts
        if case .full(let value) = target.pin { scanned = value; fingerprint = value }
        manual = false; filling = false
    }
    private func start() {
        let extras = host.trimmingCharacters(in: .whitespacesAndNewlines) == scannedHost ? altHosts : []
        link.send(host: host, port: port, prefix: fingerprint, code: code, scanned: scanned.isEmpty ? nil : scanned,
                  assets: assets, allowCloud: link.allowCloud, alts: extras)
    }
}

private struct LiveLinkScanner: View {
    let onCode: (LiveLinkTarget) -> Void
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scenePhase) private var scenePhase
    @State private var cameraAllowed = false
    @State private var checkedPermission = false
    @State private var cameraID = UUID()
    @State private var scanning = true
    @State private var message = ""
    @State private var selectedImage: PhotosPickerItem?
    @State private var readingImage = false
    @State private var active = true

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 20) {
                    if cameraAllowed && scanning && scenePhase == .active && !readingImage {
                        QRCodeCamera(onCode: received, onFailure: {
                            message = L10n.text("相机暂不可用，可从相册选择二维码。")
                            scanning = false
                        })
                        .id(cameraID).frame(height: 320).clipShape(RoundedRectangle(cornerRadius: 20))
                        .accessibilityLabel(L10n.text("将电脑上的连接二维码放入取景框"))
                    } else {
                        Image(systemName: "qrcode.viewfinder").font(.system(size: 80)).foregroundStyle(.secondary).frame(height: 200)
                    }
                    Text(L10n.text("扫描电脑上的二维码，或手动输入地址和配对码。"))
                        .font(.footnote).foregroundStyle(.secondary)
                    if !message.isEmpty { Text(message).font(.callout) }
                    if readingImage { ProgressView(L10n.text("正在识别二维码…")) }
                    PhotosPicker(selection: $selectedImage, matching: .images, photoLibrary: .shared()) {
                        Label(L10n.text("从相册选择二维码"), systemImage: "photo")
                    }.disabled(readingImage)
                    if cameraAllowed && !scanning {
                        Button(L10n.text("继续扫描")) { message = ""; cameraID = UUID(); scanning = true }
                    } else if checkedPermission && !cameraAllowed {
                        Button(L10n.text("前往系统设置")) {
                            if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
                        }
                    }
                }.padding()
            }
            .navigationTitle(L10n.text("扫描电脑二维码"))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button(L10n.text("关闭")) { dismiss() } } }
        }
        .task {
            switch AVCaptureDevice.authorizationStatus(for: .video) {
            case .authorized: cameraAllowed = true
            case .notDetermined: cameraAllowed = await AVCaptureDevice.requestAccess(for: .video)
            default: cameraAllowed = false
            }
            checkedPermission = true
            if !cameraAllowed { message = L10n.text("未获得相机权限，仍可从相册选择二维码。") }
        }
        .onAppear { active = true }
        .onDisappear { active = false }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active && checkedPermission {
                cameraAllowed = AVCaptureDevice.authorizationStatus(for: .video) == .authorized
                if cameraAllowed && scanning { message = "" }
            }
        }
        .onChange(of: selectedImage) { _, item in
            guard let item else { return }
            readingImage = true
            scanning = false
            message = ""
            Task { @MainActor in
                defer { readingImage = false; selectedImage = nil }
                do {
                    guard let data = try await item.loadTransferable(type: Data.self) else { throw HarborError("QR_IMAGE_UNAVAILABLE") }
                    let payloads = try await Task.detached(priority: .userInitiated) { try SubscriptionQRCode.messages(in: data) }.value
                    guard !payloads.isEmpty else { throw HarborError("QR_NOT_FOUND") }
                    guard let target = payloads.compactMap(LiveLinkCheck.parseQR).first else { throw LiveLinkFailure.qr }
                    complete(target)
                } catch {
                    message = (error as? LocalizedError)?.errorDescription ?? L10n.text("未能识别图片，请换一张清晰的二维码图片。")
                }
            }
        }
    }

    private func received(_ value: String) {
        scanning = false
        guard let target = LiveLinkCheck.parseQR(value) else {
            message = L10n.text("这不是连接电脑的二维码。")
            return
        }
        complete(target)
    }

    private func complete(_ target: LiveLinkTarget) {
        guard active else { return }
        scanning = false
        onCode(target)
        dismiss()
    }
}
