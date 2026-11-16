# Air Remote Windows PC receiver
# Run in an interactive Windows desktop session:
#   powershell -ExecutionPolicy Bypass -File .\pc_receiver.ps1
# Allow inbound TCP/UDP 9090 in Windows Firewall when prompted.

$ErrorActionPreference = "Stop"

Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;

public static class AirRemoteNative {
    [StructLayout(LayoutKind.Sequential)]
    private struct INPUT {
        public uint type;
        public InputUnion data;
    }

    [StructLayout(LayoutKind.Explicit)]
    private struct InputUnion {
        [FieldOffset(0)] public MOUSEINPUT mouse;
    }

    [StructLayout(LayoutKind.Sequential)]
    private struct MOUSEINPUT {
        public int dx;
        public int dy;
        public uint mouseData;
        public uint dwFlags;
        public uint time;
        public IntPtr dwExtraInfo;
    }

    [DllImport("user32.dll", SetLastError = true)]
    private static extern uint SendInput(uint inputs, INPUT[] input, int size);

    [DllImport("user32.dll", SetLastError = true)]
    private static extern void keybd_event(byte virtualKey, byte scanCode, uint flags, UIntPtr extraInfo);

    private const uint INPUT_MOUSE = 0;
    private const uint MOUSEEVENTF_MOVE = 0x0001;
    private const uint MOUSEEVENTF_LEFTDOWN = 0x0002;
    private const uint MOUSEEVENTF_LEFTUP = 0x0004;
    private const uint MOUSEEVENTF_RIGHTDOWN = 0x0008;
    private const uint MOUSEEVENTF_RIGHTUP = 0x0010;
    private const uint MOUSEEVENTF_MIDDLEDOWN = 0x0020;
    private const uint MOUSEEVENTF_MIDDLEUP = 0x0040;
    private const uint MOUSEEVENTF_WHEEL = 0x0800;
    private const uint MOUSEEVENTF_HWHEEL = 0x01000;

    private static void SendMouse(int dx, int dy, uint flags, uint data) {
        INPUT[] inputs = new INPUT[1];
        inputs[0].type = INPUT_MOUSE;
        inputs[0].data.mouse = new MOUSEINPUT {
            dx = dx,
            dy = dy,
            mouseData = data,
            dwFlags = flags,
            time = 0,
            dwExtraInfo = IntPtr.Zero
        };
        if (SendInput(1, inputs, Marshal.SizeOf(typeof(INPUT))) != 1) {
            throw new System.ComponentModel.Win32Exception(Marshal.GetLastWin32Error());
        }
    }

    public static void Move(int dx, int dy) {
        SendMouse(dx, dy, MOUSEEVENTF_MOVE, 0);
    }

    public static void Button(string button, bool down) {
        uint flags;
        if (button == "right") {
            flags = down ? MOUSEEVENTF_RIGHTDOWN : MOUSEEVENTF_RIGHTUP;
        } else if (button == "middle") {
            flags = down ? MOUSEEVENTF_MIDDLEDOWN : MOUSEEVENTF_MIDDLEUP;
        } else {
            flags = down ? MOUSEEVENTF_LEFTDOWN : MOUSEEVENTF_LEFTUP;
        }
        SendMouse(0, 0, flags, 0);
    }

    public static void Wheel(int delta, bool horizontal) {
        SendMouse(0, 0, horizontal ? MOUSEEVENTF_HWHEEL : MOUSEEVENTF_WHEEL, unchecked((uint)delta));
    }

    public static void VirtualKey(byte virtualKey) {
        const uint KEYEVENTF_KEYUP = 0x0002;
        keybd_event(virtualKey, 0, 0, UIntPtr.Zero);
        keybd_event(virtualKey, 0, KEYEVENTF_KEYUP, UIntPtr.Zero);
    }
}
"@

Add-Type -AssemblyName System.Windows.Forms

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
        # SendInput receives signed relative deltas, so negative movement is valid.
        [AirRemoteNative]::Move($x, $y)
    }
}

function Invoke-AirRemoteMouseButton([string]$Button, [bool]$Down) {
    [AirRemoteNative]::Button($Button.ToLowerInvariant(), $Down)
}

function Invoke-AirRemoteClick([string]$Button) {
    Invoke-AirRemoteMouseButton $Button $true
    Invoke-AirRemoteMouseButton $Button $false
}

function Invoke-AirRemoteScroll([double]$Dx, [double]$Dy) {
    if ([Math]::Abs($Dy) -gt 0.1) {
        $wheelDelta = [int][Math]::Round($Dy * 120)
        [AirRemoteNative]::Wheel($wheelDelta, $false)
    }
    if ([Math]::Abs($Dx) -gt 0.1) {
        $wheelDelta = [int][Math]::Round($Dx * 120)
        [AirRemoteNative]::Wheel($wheelDelta, $true)
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
        "WIN"       { 0x5B }
        "VOL_UP"    { 0xAF }
        "VOLUME_UP" { 0xAF }
        "VOL_DOWN"  { 0xAE }
        "VOLUME_DOWN" { 0xAE }
        "MUTE"      { 0xAD }
        "POWER"     { 0x5F } # system sleep key; does not force shutdown
        default     { 0 }
    }

    if ($virtualKey -ne 0) {
        [AirRemoteNative]::VirtualKey([byte]$virtualKey)
        return
    }

    throw "Unsupported PC key command: $Key"
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
            "mouse_down" { Invoke-AirRemoteMouseButton ([string]$message.button) $true }
            "mouse_up"   { Invoke-AirRemoteMouseButton ([string]$message.button) $false }
            "scroll"     { Invoke-AirRemoteScroll ([double]$message.dx) ([double]$message.dy) }
            "key"        { Invoke-AirRemoteKey ([string]$message.key) }
            "type"       { Invoke-AirRemoteText ([string]$message.text) }
            "ping"       { } # UDP discovery is handled separately.
            default      { throw "Unknown Air Remote command: $($message.cmd)" }
        }
        return $true
    } catch {
        Write-Warning "Ignored invalid Air Remote command: $($_.Exception.Message)"
        return $false
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
            $writer = [System.IO.StreamWriter]::new($stream, [System.Text.Encoding]::UTF8, 1024, $true)
            $writer.AutoFlush = $true

            $ready = (@{ cmd = "ready"; protocol = 1 } | ConvertTo-Json -Compress)
            $writer.WriteLine($ready)

            while ($tcpClient.Connected) {
                $line = $reader.ReadLine()
                if ($null -eq $line) { break }
                $processed = Invoke-AirRemoteCommand $line
                if ($processed) {
                    try {
                        $request = $line | ConvertFrom-Json
                        if ($null -ne $request.id) {
                            $ack = (@{ cmd = "ack"; id = [string]$request.id } | ConvertTo-Json -Compress)
                            $writer.WriteLine($ack)
                        }
                    } catch {
                        Write-Warning "Could not acknowledge command: $($_.Exception.Message)"
                    }
                }
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
