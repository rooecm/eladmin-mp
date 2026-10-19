# Regex group experiment for the brace-insertion pattern
$pattern = [regex]::new('(?m)^([ \t]*)((?:if|for|while)[ \t]*\((.*)\)[ \t]+([^{}].*?));[ \t]*$')
$samples = @(
    '        if (e.getStatus() == 401) builder.header("WWW-Authenticate", "Bearer");',
    '        if (tokens.isValid(token) && !authenticate(request, response, token)) return;',
    '        for (int id = 1; id <= 3; id++) sql.update("INSERT INTO t(id) VALUES(?, ?)", id, hash);'
)
foreach ($s in $samples) {
    $m = $pattern.Match($s)
    if ($m.Success) {
        'INPUT : ' + $s
        '  G2  : ' + $m.Groups[2].Value
        '  G4  : ' + $m.Groups[4].Value
        '  FIX : ' + $m.Groups[1].Value + $m.Groups[2].Value + ' { ' + $m.Groups[4].Value + '; }'
    } else {
        'INPUT : ' + $s
        '  NO MATCH'
    }
}
