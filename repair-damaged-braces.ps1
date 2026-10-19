# Repairs lines damaged by the faulty brace-insertion script.
# Damaged shape: <indent><cf>(<M>)<S> { <S>; }   where the statement S appears twice
# and the ')' consumed by '\)' must be restored to rebuild the original statement.
$ErrorActionPreference = 'Stop'
$root = 'D:\eladmin-mp'
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
$utf8Bom   = [System.Text.UTF8Encoding]::new($true)
$files = Get-ChildItem -Recurse -Filter *.java -Path "$root\rooe-admin", "$root\rooe-starter" |
    Where-Object { $_.FullName -notmatch '\\target\\' }
# G1 indent | G2 "cf(" | G3 inner(cond-misaligned region) | G5 separator | G6 S
$pattern = [regex]::new('(?m)^([ \t]*)((?:if|for|while)[ \t]*\()([^{}]*?)( \{ )([^{}]*); \}[ \t]*\r?$')

function Scan-Cond([string]$inner) {
    # Returns index of the ')' that closes the control-flow head (depth back to 0), or -1.
    $depth = 0; $inStr = $false; $inChar = $false
    for ($i = 0; $i -lt $inner.Length; $i++) {
        $c = $inner[$i]
        if ($inStr) { if ($c -eq '\') { $i++ } elseif ($c -eq '"') { $inStr = $false }; continue }
        if ($inChar) { if ($c -eq '\') { $i++ } elseif ($c -eq "'") { $inChar = $false }; continue }
        if ($c -eq '"') { $inStr = $true; continue }
        if ($c -eq "'") { $inChar = $true; continue }
        if ($c -eq '(') { $depth++ }
        elseif ($c -eq ')') { $depth--; if ($depth -eq 0) { return $i } }
    }
    return -1
}

$total = 0
$touched = @()
foreach ($f in $files) {
    $text = [System.IO.File]::ReadAllText($f.FullName)
    $count = 0
    $new = $pattern.Replace($text, {
        param($m)
        $indent = $m.Groups[1].Value
        $cfHead = $m.Groups[2].Value          # like "if (" / "for ("
        $inner  = $m.Groups[3].Value          # content up to (but excluding) the " { " separator
        $S      = $m.Groups[5].Value          # statement inside braces
        # Damaged lines only: text before " { " must end with " " + S
        if (-not $inner.EndsWith(' ' + $S)) { return $m.Value }
        # Strip trailing " <S>" then the trailing ')' consumed by the faulty regex
        $headNoClose = $inner.Substring(0, $inner.Length - $S.Length - 1)
        if (-not $headNoClose.EndsWith(')')) { return $m.Value }
        $openIdx = $headNoClose.IndexOf('(')
        $M = $headNoClose.Substring($openIdx + 1, $headNoClose.Length - $openIdx - 2)
        $condEnd = Scan-Cond $M
        if ($condEnd -ge 0) {
            $cond = $M.Substring(0, $condEnd)
            $rest = $M.Substring($condEnd + 1)
            $stmtFull = $rest + ')' + $S
        } else {
            $cond = $M
            $stmtFull = $S
        }
        $script:total++
        $script:count++
        return $indent + $cfHead + $cond + ') { ' + $stmtFull + '; }'
    })
    $count = $script:count
    if ($count -gt 0) {
        $bytes = [System.IO.File]::ReadAllBytes($f.FullName)
        $enc = if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) { $utf8Bom } else { $utf8NoBom }
        [System.IO.File]::WriteAllText($f.FullName, $new, $enc)
        $touched += "$count`t$($f.FullName.Substring($root.Length + 1))"
    }
}
$touched
"TOTAL REPAIRED: $total"
