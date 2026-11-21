#requires -Version 7.3
[CmdletBinding()]
param(
    [string] $Sdk = $env:ANDROID_HOME,
    [string] $ApiAar,
    [Parameter(Mandatory)] [string] $KeyStore,
    [Parameter(Mandatory)] [string] $PasswordFile,
    [ValidatePattern('^[0-9a-fA-F]{64}$')]
    [string] $ExpectedSignerSha256 = '4b6f3dd58635b9dbc0553fcc7f748244dc44a57943a98821ff3fbe261de2f906'
)

$ErrorActionPreference = 'Stop'
$PSNativeCommandUseErrorActionPreference = $true
$project = $PSScriptRoot
if (!$Sdk) { $Sdk = $env:ANDROID_SDK_ROOT }
if (!$Sdk) { $Sdk = Split-Path -Parent (Split-Path -Parent (Get-Command adb).Source) }
$sdkTools = Join-Path $Sdk 'build-tools/37.0.0'
$platform = Join-Path $Sdk 'platforms/android-37.0/android.jar'
if (!(Test-Path -LiteralPath $platform)) { $platform = Join-Path $Sdk 'platforms/android-37/android.jar' }
$javaBin = Join-Path $env:JAVA_HOME 'bin'
$work = (New-Item -ItemType Directory -Path (Join-Path ([IO.Path]::GetTempPath()) ("appops-module-" + [guid]::NewGuid()))).FullName
$source = Join-Path $project 'app/src/main'
$KeyStore = (Resolve-Path -LiteralPath $KeyStore).Path
$PasswordFile = (Resolve-Path -LiteralPath $PasswordFile).Path
$appConfig = Get-Content -Raw -LiteralPath (Join-Path $project 'app/build.gradle.kts')
if ($appConfig -notmatch '(?m)^\s*versionCode\s*=\s*(\d+)\s*$') { throw 'Missing versionCode in app/build.gradle.kts' }
$versionCode = $Matches[1]
if ($appConfig -notmatch '(?m)^\s*versionName\s*=\s*"([^"]+)"\s*$') { throw 'Missing versionName in app/build.gradle.kts' }
$versionName = $Matches[1]
if (!$ApiAar) {
    $ApiAar = Join-Path $work 'api-102.0.0.aar'
    Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/io/github/libxposed/api/102.0.0/api-102.0.0.aar' -OutFile $ApiAar
}
if ((Get-FileHash $ApiAar -Algorithm SHA256).Hash -ne '423484a6e1807e7a423c4b88fcd8176d104318259d91791877fed88fe91479d0') {
    throw 'libxposed API 102.0.0 AAR digest mismatch'
}

$apiJar = Join-Path $work 'libxposed-compile-only.jar'
$aar = [IO.Compression.ZipFile]::OpenRead((Resolve-Path -LiteralPath $ApiAar))
try {
    [IO.Compression.ZipFileExtensions]::ExtractToFile($aar.GetEntry('classes.jar'), $apiJar)
} finally { $aar.Dispose() }

$classes = (New-Item -ItemType Directory (Join-Path $work 'classes')).FullName
$sources = @(Get-ChildItem -Recurse -File (Join-Path $source 'java') -Filter '*.java' | ForEach-Object FullName)
& (Join-Path $javaBin 'javac.exe') -encoding UTF-8 --release 17 -classpath "$platform;$apiJar" -d $classes @sources
$classFiles = @(Get-ChildItem -Recurse -File $classes -Filter '*.class' | ForEach-Object FullName)
$java = Join-Path $javaBin 'java.exe'
$dex = (New-Item -ItemType Directory (Join-Path $work 'dex')).FullName
# SDK Build Tools 37.0.0 ships a D8 that accepts API 36 as its highest bytecode floor.
& $java -cp (Join-Path $sdkTools 'lib/d8.jar') com.android.tools.r8.D8 --release --min-api 36 --lib $platform --classpath $apiJar --output $dex @classFiles

$manifest = Join-Path $work 'AndroidManifest.xml'
$xml = [IO.File]::ReadAllText((Join-Path $source 'AndroidManifest.xml'))
[IO.File]::WriteAllText($manifest, $xml.Replace('<manifest ', '<manifest package="local.appops.compat" '))
$resources = Join-Path $work 'resources.zip'
$unsigned = Join-Path $work 'unsigned.apk'
$aapt = Join-Path $sdkTools 'aapt2.exe'
& $aapt compile --dir (Join-Path $source 'res') -o $resources
& $aapt link -o $unsigned --manifest $manifest -I $platform --min-sdk-version 36 --target-sdk-version 37 --version-code $versionCode --version-name $versionName $resources

$apk = [IO.Compression.ZipFile]::Open($unsigned, [IO.Compression.ZipArchiveMode]::Update)
try {
    [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($apk, (Join-Path $dex 'classes.dex'), 'classes.dex') | Out-Null
    $metadata = Join-Path $source 'resources'
    foreach ($file in Get-ChildItem -Recurse -File $metadata) {
        $path = [IO.Path]::GetRelativePath($metadata, $file.FullName).Replace('\', '/')
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($apk, $file.FullName, $path) | Out-Null
    }
} finally { $apk.Dispose() }

$aligned = Join-Path $work 'aligned.apk'
& (Join-Path $sdkTools 'zipalign.exe') -f 4 $unsigned $aligned
$outputDir = (New-Item -ItemType Directory -Force -Path (Join-Path $project 'build')).FullName
$output = Join-Path $outputDir 'AppOps-Android16-17-LSPosed.apk'
$signed = Join-Path $work 'signed.apk'
$signer = Join-Path $sdkTools 'lib/apksigner.jar'
& $java -jar $signer sign --ks $KeyStore --ks-pass "file:$PasswordFile" --v4-signing-enabled false --out $signed $aligned
$certificates = & $java -jar $signer verify --print-certs $signed
$fingerprints = @($certificates | Select-String 'certificate SHA-256 digest:\s*([a-fA-F0-9]{64})\s*$' |
    ForEach-Object { $_.Matches[0].Groups[1].Value } | Sort-Object -Unique)
if ($fingerprints.Count -ne 1 -or $fingerprints[0] -ne $ExpectedSignerSha256) {
    throw 'The APK signer does not match the expected SHA-256 certificate fingerprint.'
}
& (Join-Path $sdkTools 'zipalign.exe') -c 4 $signed
Copy-Item -LiteralPath $signed -Destination $output -Force
Write-Output $output
