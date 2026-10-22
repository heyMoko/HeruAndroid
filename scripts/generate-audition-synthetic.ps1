$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Speech
$projectRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$out = Join-Path $projectRoot 'content\voice\audition-synthetic-v1'
New-Item -ItemType Directory -Force -Path $out | Out-Null
$s = New-Object System.Speech.Synthesis.SpeechSynthesizer
$v = $s.GetInstalledVoices() | Where-Object { $_.VoiceInfo.Culture.Name -eq 'ko-KR' } | Select-Object -First 1
if (-not $v) { throw 'A Korean Windows speech voice is required.' }
$s.SelectVoice($v.VoiceInfo.Name)
$s.Volume = 85
$items = @(
  @{ Name='audition_A_level1.wav'; Pitch='default'; Text='식사 시간이네. 장난감을 내려놓고 식탁에 앉아 볼까?' },
  @{ Name='audition_A_level5.wav'; Pitch='default'; Text='누구야! 누가 밥 먹을 시간에 아직 놀고 있어! 엄마가 식사 시간이라고 했지! 장난감 내려놓고, 얼른 식탁에 앉아라!' },
  @{ Name='audition_B_level1.wav'; Pitch='-8%'; Text='식사 시간이네. 장난감을 내려놓고 식탁에 앉아 볼까?' },
  @{ Name='audition_B_level5.wav'; Pitch='-8%'; Text='누구야! 누가 밥 먹을 시간에 아직 놀고 있어! 엄마가 식사 시간이라고 했지! 장난감 내려놓고, 얼른 식탁에 앉아라!' },
  @{ Name='audition_C_level1.wav'; Pitch='+4%'; Text='식사 시간이네. 장난감을 내려놓고 식탁에 앉아 볼까?' },
  @{ Name='audition_C_level5.wav'; Pitch='+4%'; Text='누구야! 누가 밥 먹을 시간에 아직 놀고 있어! 엄마가 식사 시간이라고 했지! 장난감 내려놓고, 얼른 식탁에 앉아라!' }
)
try {
  foreach ($item in $items) {
    $safeText = [System.Security.SecurityElement]::Escape($item.Text)
    $ssml = "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='ko-KR'><prosody pitch='$($item.Pitch)' rate='0%'>$safeText</prosody></speak>"
    $path = Join-Path $out $item.Name
    $s.SetOutputToWaveFile($path)
    $s.SpeakSsml($ssml)
    $s.SetOutputToNull()
  }
} finally { $s.Dispose() }
Write-Output "Created $($items.Count) synthetic audition WAV clips in $out"
