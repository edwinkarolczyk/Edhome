# EDHOME Beta DEV — jednorazowe przygotowanie prywatnego klucza APK.
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

if (Test-Path -LiteralPath $keyFile) {
    Write-Host "Klucz juz istnieje: $keyFile" -ForegroundColor Yellow
    Write-Host 'Nie generuje nowego klucza: podmiana zablokowalaby aktualizacje.'
} else {
    New-Item -ItemType Directory -Force -Path $keyDirectory | Out-Null
    Write-Host 'EDHOME Beta DEV — generowanie TRWALEGO klucza Android.' -ForegroundColor Cyan
    Write-Host 'Wpisz nowe mocne haslo w konsoli keytool. Nie wpisuj go do czatu.'
    Write-Host 'Dla PKCS12 haslo klucza i magazynu jest takie samo.'
    & $keytool -genkeypair -v -keystore $keyFile -storetype PKCS12 -alias $alias -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=EDHOME Beta, OU=Development, O=EDHOME, C=PL'
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $keyFile)) {
        throw 'Generowanie klucza sie nie powiodlo. Nie konfiguruj sekretow z niekompletnego pliku.'
    }
    Write-Host "Utworzono klucz: $keyFile" -ForegroundColor Green
}

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
