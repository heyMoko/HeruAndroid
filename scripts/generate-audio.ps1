$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Speech
$heruRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$heruAudio = Join-Path $heruRoot 'app\src\main\assets\audio'
[System.IO.Directory]::CreateDirectory($heruAudio) | Out-Null
$heruSpeech = New-Object System.Speech.Synthesis.SpeechSynthesizer
$heruVoice = $heruSpeech.GetInstalledVoices() | Where-Object { $_.VoiceInfo.Culture.Name -eq 'ko-KR' } | Select-Object -First 1
if (-not $heruVoice) { throw 'A Korean Windows speech voice is required.' }
$heruSpeech.SelectVoice($heruVoice.VoiceInfo.Name)
$heruSpeech.Volume = 85
$heruMain = @(
    '식사 시간이네. 장난감을 내려놓고 식탁에 앉아 볼까?',
    '이제 식사 시간이야. 장난감을 내려놓고 식탁에 앉자.',
    '지금은 식사 시간이야. 장난감을 내려놓고 식탁에 앉아야 해.',
    '식사 시간이다. 이제 놀이를 멈추고, 장난감을 내려놓고 식탁에 앉아라.',
    '누구야! 누가 밥 먹을 시간에 아직 놀고 있어! {0}가 식사 시간이라고 했지! 장난감 내려놓고, 얼른 식탁에 앉아라!'
)
try {
    for ($heruLevel = 1; $heruLevel -le 5; $heruLevel++) {
        $heruSpeech.Rate = if ($heruLevel -lt 4) { -1 } else { -2 }
        foreach ($heruGuardian in @(@{Key='mom';Label='엄마'}, @{Key='dad';Label='아빠'})) {
            $heruText = $heruMain[$heruLevel - 1] -f $heruGuardian.Label
            $heruPath = Join-Path $heruAudio ("main_{0}_{1}.wav" -f $heruLevel, $heruGuardian.Key)
            $heruSpeech.SetOutputToWaveFile($heruPath)
            $heruSpeech.Speak($heruText)
            $heruSpeech.SetOutputToNull()
        }
        $heruFollow = if ($heruLevel -le 2) {
            '지금 할 일은 식탁에 앉는 거야. 도움이 필요하면 엄마나 아빠에게 말해 줘.'
        } elseif ($heruLevel -eq 3) {
            '장난감을 내려놓고 식탁에 앉자. 도움이 필요하면 엄마나 아빠에게 말해.'
        } else {
            '장난감을 내려놓고 식탁에 앉아라. 도움이 필요하면 엄마나 아빠에게 말해라.'
        }
        $heruSpeech.SetOutputToWaveFile((Join-Path $heruAudio ("follow_{0}.wav" -f $heruLevel)))
        $heruSpeech.Speak($heruFollow)
        $heruSpeech.SetOutputToNull()
    }
    $heruSpeech.Rate = -1
    $heruSpeech.SetOutputToWaveFile((Join-Path $heruAudio 'finish.wav'))
    $heruSpeech.Speak('자리에 앉았구나. 잘했다. 나는 이만 가마.')
    $heruSpeech.SetOutputToNull()
} finally { $heruSpeech.Dispose() }
Write-Output 'Created 16 temporary Korean WAV clips.'
