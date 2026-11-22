#requires -Version 7.3
[CmdletBinding()]
param(
    [string] $Sdk = $env:ANDROID_HOME,
    [string] $ApiAar,
    [string] $DependencyCache,
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
if ($appConfig -notmatch '(?m)^\s*minSdk\s*=\s*(\d+)\s*$') { throw 'Missing minSdk in app/build.gradle.kts' }
$minSdk = $Matches[1]
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

$dependencies = @(
    'org/smali/dexlib2/2.5.2/dexlib2-2.5.2.jar 5a5c8982d8bd7d6e3bb1a0713049e3c78b719ec32b20f6b619885cec30a0dd61'
    'com/google/guava/guava/27.1-android/guava-27.1-android.jar 686404f2d1d4d221911f96bd627ff60dac2226a5dfa6fb8ba517073eb97ec0ef'
    'com/google/guava/failureaccess/1.0.1/failureaccess-1.0.1.jar a171ee4c734dd2da837e4b16be9df4661afab72a41adaf31eb84dfdaf936ca26'
    'com/google/code/findbugs/jsr305/3.0.2/jsr305-3.0.2.jar 766ad2a0783f2687962c8ad74ceecc38a28b9f72a2d085ee438b7813e928d0c7'
    'dev/rikka/shizuku/api/13.1.5/api-13.1.5.aar 4def9bde498ef8626614c2fc5db9af4749c86f16f6c33e3f5658d35e70bab59b'
    'dev/rikka/shizuku/aidl/13.1.5/aidl-13.1.5.aar 33fe7191cdd69fcb66d649264f3b0c47acb2f3d6343afc05b98dbbff6f221963'
    'dev/rikka/shizuku/shared/13.1.5/shared-13.1.5.aar 4659642c9339be0a26e9c65bb8648f7ad6d8f4a465f557993ccbc78802381635'
    'dev/rikka/shizuku/provider/13.1.5/provider-13.1.5.aar b0f18cd9812464ec171c53cac93a819fe411718a3965c311f01eb4de265381b3'
)
if (!$DependencyCache) { $DependencyCache = $work }
$runtimeJars = @(foreach ($entry in $dependencies) {
    $path, $digest = $entry.Split(' ')
    $artifact = Join-Path $DependencyCache ([IO.Path]::GetFileName($path))
    if (!(Test-Path -LiteralPath $artifact)) {
        Invoke-WebRequest -Uri ("https://repo.maven.apache.org/maven2/" + $path) -OutFile $artifact
    }
    if ((Get-FileHash -LiteralPath $artifact -Algorithm SHA256).Hash -ne $digest) {
        throw "Dependency digest mismatch: $path"
    }
    if ($artifact.EndsWith('.aar')) {
        $jar = Join-Path $work ([IO.Path]::ChangeExtension([IO.Path]::GetFileName($path), '.jar'))
        $archive = [IO.Compression.ZipFile]::OpenRead($artifact)
        try { [IO.Compression.ZipFileExtensions]::ExtractToFile($archive.GetEntry('classes.jar'), $jar) }
        finally { $archive.Dispose() }
        $jar
    } else { $artifact }
})
$classes = (New-Item -ItemType Directory (Join-Path $work 'classes')).FullName
$sources = @(Get-ChildItem -Recurse -File (Join-Path $source 'java') -Filter '*.java' | ForEach-Object FullName)
$compileClasspath = (@($platform, $apiJar) + $runtimeJars) -join [IO.Path]::PathSeparator
& (Join-Path $javaBin 'javac.exe') -encoding UTF-8 --release 17 -classpath $compileClasspath -d $classes @sources
$classFiles = @(Get-ChildItem -Recurse -File $classes -Filter '*.class' | ForEach-Object FullName)
$java = Join-Path $javaBin 'java.exe'
$dex = (New-Item -ItemType Directory (Join-Path $work 'dex')).FullName
& $java -cp (Join-Path $sdkTools 'lib/d8.jar') com.android.tools.r8.D8 --release --min-api $minSdk --lib $platform --classpath $apiJar --output $dex @classFiles @runtimeJars

$manifest = Join-Path $work 'AndroidManifest.xml'
$xml = [IO.File]::ReadAllText((Join-Path $source 'AndroidManifest.xml'))
[IO.File]::WriteAllText($manifest, $xml.Replace('<manifest ', '<manifest package="local.appops.compat" '))
$resources = Join-Path $work 'resources.zip'
$unsigned = Join-Path $work 'unsigned.apk'
$aapt = Join-Path $sdkTools 'aapt2.exe'
& $aapt compile --dir (Join-Path $source 'res') -o $resources
& $aapt link -o $unsigned --manifest $manifest -I $platform --min-sdk-version $minSdk --target-sdk-version 37 --version-code $versionCode --version-name $versionName $resources

$apk = [IO.Compression.ZipFile]::Open($unsigned, [IO.Compression.ZipArchiveMode]::Update)
try {
    foreach ($file in Get-ChildItem -LiteralPath $dex -Filter 'classes*.dex' -File) {
        [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($apk, $file.FullName, $file.Name) | Out-Null
    }
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
