"""Keep native launch wiring consistent with the exercised Swift coordinator.

The full app's Python/proxy binaries are device-only. CI runs the real Swift
startup coordinator with an isolated Keychain, while these checks guard the
actual app call site against accidentally restoring inside runtime boot again.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]


class SessionStartupWiringTests(unittest.TestCase):
    def test_runtime_boot_cannot_authenticate_before_routing_boot(self):
        source = (ROOT / "Sources/Core/Engine.swift").read_text(encoding="utf-8")
        boot = source.split("func boot() async {", 1)[1].split("func restoreSavedSession()", 1)[0]
        self.assertNotIn("await restoreSavedSession()", boot)
        self.assertNotIn("await connect(", boot)
        self.assertNotIn("KeychainStore.load", boot)

    def test_real_model_prepares_routing_then_authenticates_before_account_work(self):
        source = (ROOT / "Sources/Core/HarborModel.swift").read_text(encoding="utf-8")
        boot = source.split("func boot() async {", 1)[1].split("private func fatalStorage", 1)[0]
        phases = ["await engine.boot()", "await SessionStartup.restore(prepareRouting:",
                  "await self.coreAdapter.probe()", "ProxyManager.shared.coreValidator =",
                  "authenticate: { await self.engine.restoreSavedSession() }",
                  "try await loadSettings()", "transfer.activate()", "transferDisposition(activeAccount: account)"]
        positions = [boot.index(phase) for phase in phases]
        self.assertEqual(positions, sorted(positions))
