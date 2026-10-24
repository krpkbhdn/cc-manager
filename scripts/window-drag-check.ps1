Add-Type @"
using System; using System.Runtime.InteropServices;
public class D {
 [DllImport("user32.dll")] public static extern bool SetCursorPos(int x, int y);
 [DllImport("user32.dll")] public static extern void mouse_event(uint f, int dx, int dy, uint d, UIntPtr e);
 [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
 [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
 [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int c);
 [DllImport("user32.dll")] public static extern bool IsZoomed(IntPtr h);
 [StructLayout(LayoutKind.Sequential)] public struct RECT { public int L,T,R,B; }
}
"@
$p = $null
for ($i=0; $i -lt 120 -and -not $p; $i++) { Start-Sleep 1; $p = Get-Process java -ErrorAction SilentlyContinue | Where-Object { $_.MainWindowTitle -like "Claude Code Manager*" } | Select-Object -First 1 }
if (-not $p) { "NO WINDOW"; exit }
$h = $p.MainWindowHandle; Start-Sleep 3
[D]::ShowWindow($h, 9) | Out-Null; [D]::SetForegroundWindow($h) | Out-Null; Start-Sleep 1
$r = New-Object D+RECT; [D]::GetWindowRect($h, [ref]$r) | Out-Null
"before: $($r.L),$($r.T) - $($r.R),$($r.B)"
$x = $r.L + 200; $y = $r.T + 20
[D]::SetCursorPos($x, $y) | Out-Null; Start-Sleep -Milliseconds 300
[D]::mouse_event(0x2, 0, 0, 0, [UIntPtr]::Zero); Start-Sleep -Milliseconds 300
for ($i=1; $i -le 30; $i++) { [D]::SetCursorPos($x + 8*$i, $y + 4*$i) | Out-Null; Start-Sleep -Milliseconds 15 }
Start-Sleep -Milliseconds 300
[D]::mouse_event(0x4, 0, 0, 0, [UIntPtr]::Zero); Start-Sleep -Milliseconds 500
$r2 = New-Object D+RECT; [D]::GetWindowRect($h, [ref]$r2) | Out-Null
"after:  $($r2.L),$($r2.T) - $($r2.R),$($r2.B)"
if ($r2.L -ne $r.L -or $r2.T -ne $r.T) { "DRAG MOVED THE WINDOW (dx=$($r2.L-$r.L) dy=$($r2.T-$r.T))" } else { "DRAG DID NOT MOVE THE WINDOW" }
Stop-Process -Id $p.Id -Force
