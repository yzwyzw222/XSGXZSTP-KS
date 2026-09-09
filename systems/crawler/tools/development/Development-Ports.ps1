function Get-DevelopmentPortStatus([ValidateRange(1, 65535)][int]$Port) {
    # Windows 对已独占的监听端口也可能返回 AccessDenied，先识别占用以免误选备用端口。
    $listeners = [Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners()
    foreach ($endpoint in $listeners) {
        if ($endpoint.Port -eq $Port -and $endpoint.Address -in @([Net.IPAddress]::Loopback, [Net.IPAddress]::Any, [Net.IPAddress]::IPv6Any)) {
            return 'Occupied'
        }
    }

    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $Port)
    try {
        $listener.Server.ExclusiveAddressUse = $true
        $listener.Start()
        return 'Available'
    } catch [Net.Sockets.SocketException] {
        switch ($_.Exception.GetBaseException().SocketErrorCode) {
            'AddressAlreadyInUse' { return 'Occupied' }
            'AccessDenied' { return 'Denied' }
            default { throw }
        }
    } finally { $listener.Stop() }
}

function Assert-DevelopmentPortAvailable([ValidateRange(1, 65535)][int]$Port) {
    switch (Get-DevelopmentPortStatus $Port) {
        'Available' { return }
        'Occupied' { throw "端口 $Port 已占用，未重复启动。请使用已有终端或核对占用进程。" }
        'Denied' { throw "系统拒绝绑定 127.0.0.1:$Port。请用 netsh interface ipv4 show excludedportrange protocol=tcp 检查系统保留端口。" }
        default { throw "无法确认端口 $Port 是否可绑定，未启动服务。" }
    }
}

function Resolve-DevelopmentFrontendPort {
    switch (Get-DevelopmentPortStatus 5173) {
        'Available' { return 5173 }
        'Occupied' { throw '端口 5173 已占用，未重复启动。请使用已有终端或核对占用进程。' }
        'Denied' {
            Assert-DevelopmentPortAvailable 15173
            Write-Warning '系统拒绝绑定前端端口 5173（可能被 Windows 保留），本次改用 http://127.0.0.1:15173/login。'
            return 15173
        }
        default { throw '无法确认前端端口 5173 是否可绑定，未启动服务。' }
    }
}
