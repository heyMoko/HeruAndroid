# Google Cloud TTS 연결 준비

이 프로젝트에는 Google Cloud 인증 키가 없으므로, 음성 API를 자동 호출하지 않는다. 아래 스크립트는 사용자가 직접 만든 Google Cloud API 키를 받아 한국어 16개 대사를 WAV로 만든다.

## 준비

1. Google Cloud 프로젝트를 만든다.
2. Cloud Text-to-Speech API를 켠다.
3. API 키를 만들고 Text-to-Speech API만 사용할 수 있도록 제한한다.
4. 프로젝트 폴더에서 다음처럼 실행한다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\generate-google-tts.ps1 -ApiKey "여기에_API_키"
```

기본 음성은 `ko-KR-Wavenet-C`다. 다른 한국어 음성은 `-VoiceName`으로 바꿔 비교할 수 있다.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\generate-google-tts.ps1 -ApiKey "여기에_API_키" -VoiceName "ko-KR-Wavenet-D"
```

생성 파일은 먼저 `app/src/main/assets/audio/google-ko-v1`에 저장한다. 들어본 뒤 선택한 파일만 기존 `audio` 폴더에 교체한다. 생성 파일을 바로 앱에 덮어쓰지 않는 이유는 음성 결과를 확인하기 전에도 기존 앱을 실행할 수 있어야 하기 때문이다.

## 비용과 권리

Google Cloud TTS는 월별 무료 사용량이 있지만, 결제 계정과 API 사용량 제한을 함께 설정해야 한다. 무료 한도는 서비스 정책에 따라 바뀔 수 있으므로 콘솔에서 확인한다. Google 문서에는 Cloud TTS로 만든 오디오를 앱이나 미디어에 사용할 수 있다고 안내되어 있다. 실제 출시 전에는 해당 프로젝트의 약관과 현재 가격을 다시 확인한다.

## 한계

이 스크립트는 안정적인 한국어 안내 음성을 만드는 연결 단계다. Wavenet 음성이 실제 배우처럼 강하게 화를 내거나 도깨비 연기를 보장하지는 않는다. 감정이 부족하면 같은 16개 대사를 실제 성우에게 녹음하는 방향으로 전환한다.
