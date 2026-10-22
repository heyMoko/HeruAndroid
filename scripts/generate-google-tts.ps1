param(
  [Parameter(Mandatory=$true)][string]$ApiKey,
  [string]$VoiceName = 'ko-KR-Wavenet-C',
  [string]$Input = 'content/voice/meal-recording-v1.json',
  [string]$Output = 'app/src/main/assets/audio/google-ko-v1'
)
$ErrorActionPreference = 'Stop'
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$inputPath = Join-Path $projectRoot $Input
$outputPath = Join-Path $projectRoot $Output
New-Item -ItemType Directory -Force -Path $outputPath | Out-Null
$clips = (Get-Content -Raw -LiteralPath $inputPath | ConvertFrom-Json).clips
$endpoint = 'https://texttospeech.googleapis.com/v1/text:synthesize'
foreach ($clip in $clips) {
  $escaped = [System.Security.SecurityElement]::Escape($clip.text)
  $level = [int]$clip.level
  $rate = if ($level -ge 4) { 0.88 } elseif ($level -eq 3) { 0.94 } else { 1.0 }
  $pitch = if ($level -ge 4) { -3.0 } elseif ($level -eq 3) { -1.0 } else { 0.0 }
  $body = @{
    input = @{ ssml = "<speak><prosody rate='$rate' pitch='$($pitch)dB'>$escaped</prosody></speak>" }
    voice = @{ languageCode = 'ko-KR'; name = $VoiceName }
    audioConfig = @{ audioEncoding = 'LINEAR16'; speakingRate = 1.0; pitch = 0.0 }
  } | ConvertTo-Json -Depth 8
  $response = Invoke-RestMethod -Method Post -Uri $endpoint -Headers @{ 'x-goog-api-key' = $ApiKey } -ContentType 'application/json; charset=utf-8' -Body $body
  $bytes = [Convert]::FromBase64String($response.audioContent)
  [IO.File]::WriteAllBytes((Join-Path $outputPath $clip.file), $bytes)
  Write-Output "Created $($clip.file)"
}
Write-Output "Created $($clips.Count) Google Cloud TTS WAV files in $outputPath"
