$filePath = 'D:\eladmin-mp\sql\eladmin.sql'
$lines = [System.IO.File]::ReadAllLines($filePath)
$newLines = New-Object System.Collections.Generic.List[string]
for ($i = 0; $i -lt $lines.Length; $i++) {
    # Skip lines 715-739 (0-based: 714-738), which is the tool_alipay_config section
    if ($i -ge 714 -and $i -le 739) {
        continue
    }
    $newLines.Add($lines[$i])
}
[System.IO.File]::WriteAllLines($filePath, $newLines.ToArray())
Write-Host "Done. Removed lines 715-740 (tool_alipay_config section)"
