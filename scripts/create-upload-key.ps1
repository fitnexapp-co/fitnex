# Creates FITNEX's Google Play upload key and keystore.properties (both git-ignored).
# Run it yourself: it asks for the passwords, which never leave this PC.
#   powershell -ExecutionPolicy Bypass -File scripts\create-upload-key.ps1
$ErrorActionPreference = "Stop"
$root = Split-Path $PSScriptRoot -Parent
$keystore = Join-Path $root "fitnex-upload.jks"
if (Test-Path $keystore) { throw "fitnex-upload.jks already exists: keep it safe, do not overwrite it." }
$keytool = Join-Path "C:\Program Files\Android\Android Studio\jbr\bin" "keytool.exe"

$secure = Read-Host "Contraseña para la clave de subida (mín. 6 caracteres)" -AsSecureString
$plain = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
if ($plain.Length -lt 6) { throw "La contraseña debe tener al menos 6 caracteres." }

& $keytool -genkeypair -v -keystore $keystore -alias fitnex-upload -keyalg RSA -keysize 4096 -validity 10000 `
    -storepass $plain -keypass $plain -dname "CN=FITNEX, O=FITNEX, C=CO"
if ($LASTEXITCODE -ne 0) { throw "keytool failed" }

@"
storeFile=fitnex-upload.jks
storePassword=$plain
keyAlias=fitnex-upload
keyPassword=$plain
"@ | Set-Content (Join-Path $root "keystore.properties") -Encoding ascii
Write-Host "Listo. Guarda una copia de fitnex-upload.jks y de la contraseña fuera de este PC: sin ellas no podrás publicar actualizaciones."
