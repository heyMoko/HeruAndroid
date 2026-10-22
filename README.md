# 해루 / Heru Android

Kotlin과 Jetpack Compose로 만든 식사 준비 상황극 앱 시제품입니다. 부모가 대사를 선택하고, 아이에게 도깨비 해루를 보여주며 생활 행동을 안내하는 흐름을 검증합니다.

## 현재 구현

- 경고 1~5단계 선택, 대사 미리듣기, 선택값 저장
- 3초 카운트다운 뒤 전신 캐릭터와 자막을 보여주는 아이 화면
- 같은 경고로 재안내 1회
- 식사 자리에 앉은 뒤 부모가 WELL 1~5 대사를 읽고 독립적으로 선택
- 칭찬 선택 → 3초 카운트다운 → 아이 화면에서 칭찬 재생
- 실제 식사 종료는 별도 버튼으로 END 음성을 재생
- 아이가 별을 터치하면 로컬 별 개수 증가, 7칸 선물 진행 표시
- 부모가 확인한 결과를 최근 200건까지 저장, 중복 기록 방지 및 기록 삭제
- 뒤로 가기, 백그라운드, 오디오 포커스 상실, 이어폰 해제 시 재생 중단
- 상세 전신 도깨비 원화와 짧은 입 열기/닫기 반복 애니메이션

## 실행

Android Studio에서 이 폴더를 엽니다. JDK 17 이상, Android SDK 37이 필요합니다. `local.properties`의 `sdk.dir`은 개발 환경에 맞게 설정하며 Git에는 포함하지 않습니다.

Windows에서 Android Studio 기본 설치 경로의 JBR을 이용하려면:

```powershell
.\build-debug.cmd assembleDebug
.\build-debug.cmd testDebugUnitTest lintDebug
```

Gradle Wrapper 9.4.1, Android Gradle Plugin 9.2.1, Kotlin/Compose compiler 2.2.10을 사용합니다. 최소 Android API는 26입니다.

## 음성과 캐릭터

앱 음성은 사용자가 직접 녹음한 WAV 11개입니다. 실시간 TTS, AI 서버, 마이크 권한, 인터넷 권한 없이 재생합니다.

| 원본 녹음 | 앱 자산 | 용도 |
| --- | --- | --- |
| 경고1~5.wav | assets/audio/main_1~5.wav | 경고 및 같은 강도 재안내 |
| well1~5.wav | assets/audio/follow_1~5.wav | 부모가 선택한 칭찬 |
| end.wav | assets/audio/finish.wav | 최종 식사 종료 |

현재 대사는 `ScriptCatalog`가 관리합니다. `content/voice`와 `docs`의 초기 제작안 및 TTS 비교 자료는 과거 검토 자료이며, 현행 대사와 파일명은 앱 코드를 기준으로 합니다.

캐릭터 원화는 `design/characters`에, 앱에서 사용하는 원화는 `res/drawable-nodpi`에 있습니다. 입 주변만 열린 원화와 닫힌 원화를 짧게 교체하며, 녹음 음량이나 발음과 동기화하지 않습니다. 자세한 방식과 생성 프롬프트는 `design/characters/mouth-animation-notes.md`를 참고하세요.

## 구조와 저장

- `domain/`: 대사와 세션 상태 전이
- `data/LocalStore`: SharedPreferences를 이용한 선택값, 별, JSON 사용 기록 저장
- `audio/ClipPlayer`: WAV 재생과 오디오 포커스 처리
- `HeruViewModel`: 부모 선택, 카운트다운, 음성, 기록의 흐름 관리
- `MainActivity`: Compose 화면 및 캐릭터 연출

아동 이름·사진·음성을 수집하지 않습니다. 저장은 기기 내부에서만 이루어지며 앱 삭제 시 사라집니다. 선물 이름·목표 별 개수 설정, 실제 선물 지급, 다중 자녀, 클라우드 동기화는 아직 구현하지 않았습니다. 현재 선물 화면은 7칸 진행 표시 시제품입니다.

## 검증 범위

디버그 빌드, 도메인 단위 테스트, Android Lint를 사용합니다. 기존 `androidTest`는 초기 화면 기준으로 작성되어 현재 전체 흐름에 맞춘 갱신이 필요합니다. 과거 `BUILD-REPORT.md`의 테스트 수와 실행 기록은 해당 시점의 자료입니다.