param([string]$File = "C:\Users\Abonen0007\Documents\svoboda2\ui_main.xml")
[xml]$doc = [System.IO.File]::ReadAllText($File, [System.Text.Encoding]::UTF8)
$nodes = $doc.SelectNodes("//node")
foreach ($n in $nodes) {
    $t = $n.text
    if ($t -and $t.Trim().Length -gt 0) {
        "{0} :: {1} :: clickable={2} enabled={3}" -f $t, $n.bounds, $n.clickable, $n.enabled
    }
}