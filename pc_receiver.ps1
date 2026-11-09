# Air Remote Windows PC receiver
# Run in an interactive Windows desktop session:
#   powershell -ExecutionPolicy Bypass -File .\pc_receiver.ps1
# Allow inbound TCP/UDP 9090 in Windows Firewall when prompted.

$ErrorActionPreference = "Stop"

Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;
public static class AirRemoteNative {
    [DllImport("user32.dll", SetLastError=true)]
    public static extern void mouse_event(uint flags, uint dx, uint dy, uint data, UIntPtr extraInfo);
    [DllImport("user32.dll", SetLastError=true)]
    public static extern void keybd_event(byte virtualKey, byte scanCode, uint flags, UIntPtr extraInfo);
}
"@

Add-Type -AssemblyName System.Windows.Forms

$MouseMove       = 0x0001
$LeftDown        = 0x0002
$LeftUp          = 0x0004
$RightDown       = 0x0008
$RightUp         = 0x0010
$Wheel           = 0x0800
$HorizontalWheel = 0x01000
$KeyUp           = 0x0002

function ConvertTo-UInt32Bits([int]$Value) {
    return [BitConverter]::ToUInt32([BitConverter]::GetBytes($Value), 0)
}

$script:mouseRemainderX = 0.0
$script:mouseRemainderY = 0.0

function Invoke-AirRemoteMouseMove([double]$Dx, [double]$Dy) {
    # Preserve subpixel deltas from the phone's gyro instead of rounding
    # every sensor frame independently (which can make slow motion stall).
    $script:mouseRemainderX += $Dx
    $script:mouseRemainderY += $Dy
    $x = [int][Math]::Truncate($script:mouseRemainderX)
    $y = [int][Math]::Truncate($script:mouseRemainderY)
    $script:mouseRemainderX -= $x
    $script:mouseRemainderY -= $y
    if ($x -ne 0 -or $y -ne 0) {
        [AirRemoteNative]::mouse_event($MouseMove, (ConvertTo-UInt32Bits $x), (ConvertTo-UInt32Bits $y), 0, [UIntPtr]::Zero)
    }
}

function Invoke-AirRemoteClick([string]$Button) {
    switch ($Button.ToLowerInvariant()) {
        "right" {
            [AirRemoteNative]::mouse_event($RightDown, 0, 0, 0, [UIntPtr]::Zero)
            [AirRemoteNative]::mouse_event($RightUp, 0, 0, 0, [UIntPtr]::Zero)
        }
        "middle" {
            [AirRemoteNative]::mouse_event(0x0020, 0, 0, 0, [UIntPtr]::Zero)
            [AirRemoteNative]::mouse_event(0x0040, 0, 0, 0, [UIntPtr]::Zero)
        }
        default {
            [AirRemoteNative]::mouse_event($LeftDown, 0, 0, 0, [UIntPtr]::Zero)
            [AirRemoteNative]::mouse_event($LeftUp, 0, 0, 0, [UIntPtr]::Zero)
        }
    }
}

function Invoke-AirRemoteScroll([double]$Dx, [double]$Dy) {
    if ([Math]::Abs($Dy) -gt 0.1) {
        $wheelDelta = [int][Math]::Round($Dy * 120)
        [AirRemoteNative]::mouse_event($Wheel, 0, 0, (ConvertTo-UInt32Bits $wheelDelta), [UIntPtr]::Zero)
    }
    if ([Math]::Abs($Dx) -gt 0.1) {
        $wheelDelta = [int][Math]::Round($Dx * 120)
        [AirRemoteNative]::mouse_event($HorizontalWheel, 0, 0, (ConvertTo-UInt32Bits $wheelDelta), [UIntPtr]::Zero)
    }
}

function Invoke-AirRemoteKey([string]$Key) {
    $name = $Key.ToUpperInvariant()
    $sendKeysMap = @{
        "UP" = "{UP}"; "DOWN" = "{DOWN}"; "LEFT" = "{LEFT}"; "RIGHT" = "{RIGHT}"
        "ENTER" = "{ENTER}"; "ESCAPE" = "{ESC}"; "BACK" = "{BACKSPACE}"
        "TAB" = "{TAB}"; "SPACE" = " "; "DELETE" = "{DELETE}"; "HOME" = "{HOME}"
        "END" = "{END}"; "PAGEUP" = "{PGUP}"; "PAGEDOWN" = "{PGDN}"
    }

    if ($sendKeysMap.ContainsKey($name)) {
        [System.Windows.Forms.SendKeys]::SendWait($sendKeysMap[$name])
        return
    }

    $virtualKey = switch ($name) {
        "WIN"       { 0x5B } # left Windows key
        "VOL_UP"    { 0xAF }
        "VOLUME_UP" { 0xAF }
        "VOL_DOWN"  { 0xAE }
        "VOLUME_DOWN" { 0xAE }
        "MUTE"      { 0xAD }
        "POWER"     { 0x5F } # system sleep key; does not force shutdown
        default     { 0 }
    }

    if ($virtualKey -ne 0) {
        [AirRemoteNative]::keybd_event([byte]$virtualKey, 0, 0, [UIntPtr]::Zero)
        [AirRemoteNative]::keybd_event([byte]$virtualKey, 0, $KeyUp, [UIntPtr]::Zero)
        return
    }

    Write-Warning "Unsupported PC key command: $Key"
}

function Escape-SendKeysText([string]$Text) {
    # Escape each original character independently. Replacing braces after
    # adding SendKeys escape tokens corrupts the tokens and loses text.
    $result = $Text.Replace("{", "__AIRREMOTE_OPEN_BRACE__").Replace("}", "__AIRREMOTE_CLOSE_BRACE__")
    foreach ($char in @("+", "^", "%", "~", "(", ")")) {
        $result = $result.Replace($char, "{$char}")
    }
    return $result.Replace("__AIRREMOTE_OPEN_BRACE__", "{{}").Replace("__AIRREMOTE_CLOSE_BRACE__", "{}}")
}

function Invoke-AirRemoteText([string]$Text) {
    if ($null -ne $Text -and $Text.Length -gt 0) {
        [System.Windows.Forms.SendKeys]::SendWait((Escape-SendKeysText $Text))
    }
}

function Invoke-AirRemoteCommand([string]$Line) {
    try {
        $message = $Line | ConvertFrom-Json
        switch ($message.cmd) {
            "mouse_move" { Invoke-AirRemoteMouseMove ([double]$message.dx) ([double]$message.dy) }
            "click"      { Invoke-AirRemoteClick ([string]$message.button) }
            "scroll"     { Invoke-AirRemoteScroll ([double]$message.dx) ([double]$message.dy) }
            "key"        { Invoke-AirRemoteKey ([string]$message.key) }
            "type"       { Invoke-AirRemoteText ([string]$message.text) }
            "ping"       { } # UDP discovery is handled separately.
            default      { Write-Warning "Unknown Air Remote command: $($message.cmd)" }
        }
    } catch {
        Write-Warning "Ignored invalid Air Remote command: $($_.Exception.Message)"
    }
}

# Respond to the Android app's UDP broadcast discovery ping.
$udpJob = Start-Job -ScriptBlock {
    $udp = [System.Net.Sockets.UdpClient]::new(9090)
    try {
        while ($true) {
            $remote = [System.Net.IPEndPoint]::new([System.Net.IPAddress]::Any, 0)
            $bytes = $udp.Receive([ref]$remote)
            $message = [System.Text.Encoding]::UTF8.GetString($bytes)
            if ($message -match '"cmd"\s*:\s*"ping"') {
                $reply = (@{ cmd = "pong"; name = $env:COMPUTERNAME } | ConvertTo-Json -Compress)
                $replyBytes = [System.Text.Encoding]::UTF8.GetBytes($reply)
                [void]$udp.Send($replyBytes, $replyBytes.Length, $remote)
            }
        }
    } finally {
        $udp.Close()
    }
}

$listener = [System.Net.Sockets.TcpListener]::new([System.Net.IPAddress]::Any, 9090)
try {
    $listener.Start()
    Write-Host "Air Remote PC receiver listening on TCP 9090 and UDP 9090." -ForegroundColor Green
    Write-Host "Keep this PowerShell window open. Press Ctrl+C to stop."
    Write-Host "If the phone cannot discover/connect, allow TCP and UDP 9090 through Windows Firewall."

    while ($true) {
        $tcpClient = $listener.AcceptTcpClient()
        $remoteAddress = $tcpClient.Client.RemoteEndPoint.ToString()
        Write-Host "Phone connected: $remoteAddress"
        try {
            $stream = $tcpClient.GetStream()
            $reader = [System.IO.StreamReader]::new($stream, [System.Text.Encoding]::UTF8)
            while ($tcpClient.Connected) {
                $line = $reader.ReadLine()
                if ($null -eq $line) { break }
                Invoke-AirRemoteCommand $line
            }
        } catch {
            Write-Warning "Receiver client ended: $($_.Exception.Message)"
        } finally {
            $tcpClient.Close()
            Write-Host "Phone disconnected: $remoteAddress"
        }
    }
} finally {
    $listener.Stop()
    if ($udpJob) {
        Stop-Job $udpJob -ErrorAction SilentlyContinue
        Remove-Job $udpJob -ErrorAction SilentlyContinue
    }
}
