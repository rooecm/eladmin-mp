# Adds braces to single-line if/for/while statements, preserving encoding/BOM/line endings.
$ErrorActionPreference = 'Stop'
$root = 'D:\eladmin-mp'
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
$utf8Bom   = [System.Text.UTF8Encoding]::new($true)
$files = Get-ChildItem -Recurse -Filter *.java -Path "$root\rooe-admin", "$root\rooe-starter" |
    Where-Object { $_.FullName -notmatch '\\target\\' }
$pattern = [regex]::new('(?m)^([ \t]*)((?:if|for|while)[ \t]*\((.*)\)[ \t]+([^{}].*?));[ \t]*(?=\r?$)')
$evaluator = [System.Text.RegularExpressions.MatchEvaluator] {
    param($m)
    $stmt = $m.Groups[4].Value
    # Defensive skips: nested control flow, braces (lambda bodies), else-chains
    if ($stmt -match '\{' -or $stmt -match '^\s*(else|if|for|while|try|do|switch)\b' -or $stmt -match ';\s*else\b') {
        return $m.Value
    }
    $script:total++
    $script:count++
    return $m.Groups[1].Value + $m.Groups[2].Value + ' { ' + $stmt + '; }'
}
$total = 0
$touched = @()
foreach ($f in $files) {
    $text = [System.IO.File]::ReadAllText($f.FullName)
    $count = 0
    $script:count = 0
    $new = $pattern.Replace($text, $evaluator)
    $count = $script:count
    if ($count -gt 0) {
        $bytes = [System.IO.File]::ReadAllBytes($f.FullName)
        $enc = if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) { $utf8Bom } else { $utf8NoBom }
        [System.IO.File]::WriteAllText($f.FullName, $new, $enc)
        $touched += "$count`t$($f.FullName.Substring($root.Length + 1))"
    }
}
$touched
"TOTAL REPLACED: $total"
