Get-ChildItem 'C:\Users\Abonen0007\Documents\svoboda2\app\build\outputs\apk\debug\*.apk' | ForEach-Object {
    $hash = (Get-FileHash $_.FullName -Algorithm SHA256).Hash
    '{0} | {1:N1} MB | SHA256 {2}' -f $_.Name, ($_.Length / 1MB), $hash
}