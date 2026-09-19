# EDHOME Beta DEV — weryfikacja JUZ UTWORZONEGO klucza i kopiowanie Base64.
# Uruchom w PowerShell na swoim komputerze. Nie wysylaj pliku .p12 ani hasel do czatu.
# Ten skrypt NIE dodaje sekretow do GitHub i NIE zapisuje hasel.
[CmdletBinding()]
param(
    [switch]$CopyBase64
)

$ErrorActionPreference = 'Stop'
$alias = 'edhome-beta'
$documents = [Environment]::GetFolderPath('MyDocuments')
if ([string]::IsNullOrWhiteSpace($documents)) {
    throw 'Nie znaleziono folderu Dokumenty.'
}
$keyDirectory = Join-Path $documents 'EDHOME-Keys'
$keyFile = Join-Path $keyDirectory 'edhome-beta.p12'

$keytool = Get-Command keytool -ErrorAction SilentlyContinue
if (-not $keytool) {
    $studioKeytool = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr\bin\keytool.exe'
    if (Test-Path -LiteralPath $studioKeytool) {
        $keytool = $studioKeytool
    }
}
if (-not $keytool) {
    throw 'Brak keytool. Zainstaluj JDK 17 lub Android Studio i uruchom skrypt ponownie.'
}

if (-not (Test-Path -LiteralPath $keyFile)) {
    throw 'Najpierw zapisz juz wygenerowany klucz EDHOME edhome-beta.p12 w Dokumenty/EDHOME-Keys. NIE generuj nowego klucza.'
}
Write-Host "Klucz istnieje: $keyFile" -ForegroundColor Green
Write-Host 'Wprowadz haslo do klucza, by sprawdzic certyfikat (haslo nie bedzie zapisane).'
& $keytool -list -v -keystore $keyFile -alias $alias | Out-String | ForEach-Object {
    if ($_ -notmatch '40:E8:EF:84:39:F2:67:76:A4:F4:95:E6:B3:95:1C:72:E8:75:9E:D9:0F:A2:6D:9A:47:6A:AC:0A:58:9A:A6:EE') {
        throw 'Certyfikat jest inny niz staly certyfikat EDHOME Beta. Przerwano.'
    }
}
if ($LASTEXITCODE -ne 0) { throw 'Nie udalo sie zweryfikowac klucza.' }
Write-Host ''
Write-Host 'Zrob KOPIE OFFLINE pliku .p12 i zachowaj haslo. Bez nich przyszle APK nie zaktualizuja obecnej wersji.' -ForegroundColor Yellow
Write-Host 'Nie dodawaj pliku klucza do repozytorium, chmury publicznej ani czatu.'
Write-Host ''
Write-Host 'GitHub -> Edhime -> Settings -> Secrets and variables -> Actions:'
Write-Host 'EDHOME_BETA_KEYSTORE_B64 = Base64 z pliku .p12'
Write-Host "EDHOME_BETA_KEY_ALIAS = $alias"
Write-Host 'EDHOME_BETA_STORE_PASSWORD = haslo wybrane w keytool'
Write-Host 'EDHOME_BETA_KEY_PASSWORD = TO SAMO haslo (PKCS12)'
Write-Host ''
Write-Host "Plik: $keyFile"
Write-Host ''
if ($CopyBase64) {
    $base64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($keyFile))
    Set-Clipboard -Value $base64
    $base64 = $null
    Write-Host 'Base64 skopiowano do schowka. Wklej do sekretu EDHOME_BETA_KEYSTORE_B64; potem wyczysc schowek.' -ForegroundColor Green
} else {
    Write-Host 'Gdy masz otwarte okno tworzenia sekretu B64, uruchom TEN SAM skrypt z parametrem -CopyBase64.' -ForegroundColor Cyan
}
Write-Host 'Adres ustawien: https://github.com/edwinkarolczyk/Edhime/settings/secrets/actions'
Write-Host ''
Write-Host 'WAŻNE: nowy klucz nie zaktualizuje w miejscu starych instalacji podpisanych innym kluczem.' -ForegroundColor Yellow
