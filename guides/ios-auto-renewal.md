# PicHarbor iOS Auto-Renewal Guide

**English** | [简体中文](ios-auto-renewal.zh-CN.md) · [Back to the project](../README.md)

Updated: November 19, 2026. For SelfRefreshKit as integrated into PicHarbor Mobile.

PicHarbor iOS includes [SelfRefreshKit](https://github.com/Cc-Cece/SelfRefreshKit). Use a computer for the initial signing, installation, and device pairing, then finish the on-device setup. After that, Shortcuts can renew the app on your iPhone without routine weekly computer connections.

“One-time setup” refers to the initial setup process. Restoring your phone, losing pairing records, or an unrecoverable signing environment may require a computer again. Renewal still depends on network access, a valid Apple session, and a local loopback VPN; it does not create a permanent certificate.

## 1. Prepare

| Device or file | Purpose |
| --- | --- |
| iPhone running iOS 17+ | Install and run PicHarbor |
| Windows computer and USB cable | Initial signing, installation, and pairing injection |
| Unsigned PicHarbor IPA | Download from a successful Mobile Actions run or use the corresponding delivered build |
| SelfRefreshSetup | Download from SelfRefreshKit Releases; the current executable is named `SelfRefreshKit.exe` |
| Apple Devices or iTunes | Provide Windows device drivers |
| .NET 10 Desktop Runtime | Required by the current Windows tool release; check the requirements of the version you download |
| Apple account | Signing and on-device renewal, including authentication and two-factor verification when prompted |
| LocalDevVPN | Provide the local loopback connection used for renewal |

Official links:

- [PicHarbor Mobile builds](https://github.com/Cc-Cece/PicHarbor-Mobile/actions)
- [SelfRefreshKit downloads](https://github.com/Cc-Cece/SelfRefreshKit/releases)
- [SelfRefreshSetup instructions](https://github.com/Cc-Cece/SelfRefreshKit/tree/master/tools/SelfRefreshSetup)
- [LocalDevVPN](https://apps.apple.com/app/localdevvpn/id6744003051)

Enable Developer Mode as requested during installation, unlock your phone, and trust the computer. Pairing records are device credentials: keep them on your own devices or in a secure backup.

## 2. Install and Pair Using the Computer

1. Connect your iPhone over USB and keep it unlocked. Tap **Trust This Computer** if prompted.
2. Open `SelfRefreshKit.exe` and select the unsigned PicHarbor IPA.
3. Enter and save your Apple account as requested. This is not your Google account. Keep the default Anisette setting unless you have a specific alternative configuration.
4. Refresh the device list and select your iPhone.
5. Choose **Sign, Install and Write Pairing**. For an already signed package, the button may read **Install and Write Pairing** instead.
6. If Apple requests a verification code, enter it in the tool's verification field.
7. Wait for both installation and pairing injection to succeed; seeing the app icon alone is not enough.

The tool handles pairing for the device's iOS version: RPPairing on iOS 17.4+ and the corresponding Lockdown records on earlier versions. Do not rename one type of pairing file to imitate the other.

Close and reopen PicHarbor to load the newly written records. If iOS asks you to trust the developer, follow the device settings prompt.

You can normally disconnect the cable now and continue on your phone. If PicHarbor cannot find the pairing records, keep the phone connected, inspect the tool's log, and write them again.

## 3. Complete On-Device Setup

Open the SelfRefreshKit renewal entry in PicHarbor **Settings**, then open **Required Setup**. Labels may vary slightly with the language and component version.

| Item | What to check |
| --- | --- |
| Import device pairing file | Completed, with device information matching this iPhone; manual import is normally unnecessary when the desktop tool has already written the records |
| Sign in to your Apple account | Authenticated; continue with the same account and developer team used for the initial signing |
| Local renewal setup | Complete the local takeover when prompted, or let an existing successful profile refresh record confirm completion |

Allow LocalDevVPN to add its VPN configuration, then connect it. This is the local loopback network for renewal, separate from the nodes used by PicHarbor's in-app proxy to access Google.

If the third item still shows **Pending**, select the **unsigned IPA for this PicHarbor app** as instructed and complete an on-device re-sign and replacement installation. The app may close temporarily; reopen it afterward. Do not uninstall the existing app to perform this step.

Current builds also recognize actual successful profile refresh records. If renewal has already succeeded and setup correctly shows completion, another replacement installation is unnecessary. Older builds had a display issue with the third item: an old 2/3 indicator alone does not prove renewal failed.

## 4. Test One Manual Renewal

1. Connect LocalDevVPN and ensure that the phone can reach the required network services.
2. Open **Health Status Check** on the renewal page and run the checks.
3. Check device pairing, the local loopback interface (`10.7.0.1`), the Anisette service, and your Apple developer session.
4. Return to the main renewal page and tap **Refresh Now**.
5. Confirm success and check both the latest renewal time and the new expiration time.

For a free account, validity normally returns to nearly seven days, such as approximately `6d 23h` immediately afterward. Check the success result, timestamps, and health status together; the fact that the app still opens is not sufficient evidence by itself.

Set up automation after this manual test succeeds. Resolve any pairing, network, or account issue first if it fails.

## 5. Configure Shortcuts Automation

Use **Optional Actions → Configure Background Auto-Refresh Shortcut** in the app to view instructions or obtain a preset. Presets may change, so use the current in-app or upstream link. After importing one, confirm that the renewal action targets **PicHarbor**.

You can also create the automation manually:

1. Open **Shortcuts → Automation** and create a **Time of Day** personal automation.
2. Schedule renewal every **5 days** at a convenient time and select **Run Immediately** so routine execution does not wait for confirmation. Do not exceed **7 days** since the last successful renewal; otherwise, reconnect to a computer to restore signing and installation.
3. Add these actions in order:

   ```text
   Connect LocalDevVPN
       ↓
   Wait 2 seconds (allow the tunnel to become ready; adjust after testing)
       ↓
   PicHarbor: Refresh App Provisioning Profile
       ↓
   Wait 5 seconds (adjust based on completion)
       ↓
   Disconnect LocalDevVPN
   ```

4. Run the shortcut manually, approve any initial permission prompts, and confirm success before relying on the schedule.
5. After the next scheduled run, check the latest renewal time and validity in PicHarbor.

The action supports background execution; adding an **Open PicHarbor** action is unnecessary. Initial authorization, renewed Apple authentication, or verification codes may still require interaction. Resolve conflicts with another VPN before testing LocalDevVPN.

A five-day renewal interval leaves time to recover from a missed or failed run before the seven-day limit. Count from the last successful renewal, not the last automation trigger. iOS execution conditions still affect automation, so check the results occasionally.

## 6. Troubleshooting

| Symptom | What to do |
| --- | --- |
| Computer cannot detect the iPhone | Unlock it, confirm trust, check the cable and Apple drivers, then refresh the device list |
| App installed but pairing is missing | Confirm that Write Pairing completed, close and reopen the app, and reconnect to the computer to write the records again if needed |
| Local loopback network is disconnected | Connect LocalDevVPN and rerun Health Check; PicHarbor's in-app proxy does not replace it |
| Apple session expired or a code is required | Authenticate again on the renewal page; the background action cannot complete interactive verification for you |
| Manual renewal works but automation fails | Check Run Immediately, the app selected for the action, initial permissions, and VPN readiness |
| Remaining validity keeps decreasing | Check the latest successful renewal and any error, then renew manually; an enabled automation alone does not confirm success |
| Is a computer still needed every week? | Not for normal renewal; use it again if pairing or the signing environment fails and cannot be restored on the phone |
| Is renewal the same as updating the app? | No. Routine renewal extends the current installation's validity; installing a newer IPA updates the app |
| More than 7 days have passed without successful renewal and the app has expired | Reconnect to a computer to restore signing and installation, preserving existing app data where possible |

## 7. Completion Checklist

- [ ] Initial signing, installation, and device pairing injection succeeded.
- [ ] Pairing and Apple authentication are healthy, and local renewal setup is complete.
- [ ] LocalDevVPN and Health Check pass.
- [ ] At least one on-device manual renewal succeeded and advanced the expiration time.
- [ ] The shortcut runs successfully by hand and targets PicHarbor.
- [ ] Renewal every 5 days is configured, the interval stays within 7 days, and an actual scheduled renewal result has been checked.

## References

Desktop steps follow the [SelfRefreshSetup instructions](https://github.com/Cc-Cece/SelfRefreshKit/blob/master/tools/SelfRefreshSetup/README.md). See [SelfRefreshKit](https://github.com/Cc-Cece/SelfRefreshKit) for the automation mechanism. Follow the labels and completion status in your current PicHarbor version; current builds can recover setup completion from actual successful renewal records.
