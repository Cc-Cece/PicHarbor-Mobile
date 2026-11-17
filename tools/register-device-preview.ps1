param([string]$Serial='691a42fe')
$ErrorActionPreference='Stop'
$mobileRoot=Split-Path -Parent $PSScriptRoot
$previewScript=Join-Path $mobileRoot 'android\tools\device-preview.ps1'
if(-not(Test-Path -LiteralPath $previewScript)){throw 'Unified mobile preview script is missing.'}
$taskName='PicHarbor-Mobile-APK-to-'+$Serial
$task=Get-ScheduledTask -TaskName $taskName -ErrorAction Stop
# Keep the existing user, triggers, repetition interval, and privilege level.
$arguments='-NoProfile -NonInteractive -ExecutionPolicy Bypass -WindowStyle Hidden -File "'+$previewScript+'" -Serial '+$Serial
$action=New-ScheduledTaskAction -Execute (Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe') -Argument $arguments -WorkingDirectory (Join-Path $mobileRoot 'android')
Set-ScheduledTask -TaskName $taskName -Action $action | Out-Null
Write-Output ('Preview task now builds from '+(Join-Path $mobileRoot 'android'))
