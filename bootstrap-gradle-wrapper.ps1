$ErrorActionPreference = "Stop"

# Run this script from the VeVak repository root.
if (-not (Test-Path ".\settings.gradle.kts") -or -not (Test-Path ".\app\build.gradle.kts")) {
    throw "Run this script from the VeVak repository root."
}

$GradleVersion = "8.11.1"
$ExpectedSha256 = "f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6"
$TempRoot = Join-Path $env:TEMP "vevak-gradle-wrapper-$GradleVersion"
$ZipPath = Join-Path $TempRoot "gradle-$GradleVersion-bin.zip"
$ExtractPath = Join-Path $TempRoot "dist"
$GradleBat = Join-Path $ExtractPath "gradle-$GradleVersion\bin\gradle.bat"

# Try to find a JDK. Android Studio includes JBR/JDK 17+.
if (-not $env:JAVA_HOME) {
    $AndroidStudioJbr = "C:\Program Files\Android\Android Studio\jbr"
    if (Test-Path (Join-Path $AndroidStudioJbr "bin\java.exe")) {
        $env:JAVA_HOME = $AndroidStudioJbr
        $env:Path = "$env:JAVA_HOME\bin;$env:Path"
    }
}

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw "Java was not found. Open Android Studio, then run this script from its Terminal, or set JAVA_HOME to Android Studio's jbr folder."
}

New-Item -ItemType Directory -Force -Path $TempRoot | Out-Null

if (-not (Test-Path $ZipPath)) {
    Write-Host "Downloading official Gradle $GradleVersion..."
    Invoke-WebRequest `
        -Uri "https://services.gradle.org/distributions/gradle-$GradleVersion-bin.zip" `
        -OutFile $ZipPath
}

$ActualSha256 = (Get-FileHash -Path $ZipPath -Algorithm SHA256).Hash.ToLowerInvariant()
if ($ActualSha256 -ne $ExpectedSha256) {
    Remove-Item $ZipPath -Force -ErrorAction SilentlyContinue
    throw "Gradle ZIP checksum mismatch. Download removed."
}

if (Test-Path $ExtractPath) {
    Remove-Item $ExtractPath -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $ExtractPath | Out-Null
Expand-Archive -Path $ZipPath -DestinationPath $ExtractPath

Write-Host "Generating the official Gradle Wrapper..."
& $GradleBat wrapper --gradle-version $GradleVersion --distribution-type bin

if ($LASTEXITCODE -ne 0) {
    throw "Gradle wrapper generation failed."
}

if (-not (Test-Path ".\gradle\wrapper\gradle-wrapper.jar")) {
    throw "Wrapper generation completed without gradle-wrapper.jar."
}

$WrapperSha256 = (Get-FileHash ".\gradle\wrapper\gradle-wrapper.jar" -Algorithm SHA256).Hash.ToLowerInvariant()
$ExpectedWrapperSha256 = "2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046"

if ($WrapperSha256 -ne $ExpectedWrapperSha256) {
    throw "Generated gradle-wrapper.jar checksum does not match Gradle's published checksum."
}

Write-Host ""
Write-Host "OK - Gradle Wrapper generated and verified."
Write-Host "Files to commit:"
Write-Host "  gradlew"
Write-Host "  gradlew.bat"
Write-Host "  gradle/wrapper/gradle-wrapper.jar"
Write-Host "  gradle/wrapper/gradle-wrapper.properties"
