# 해루 입 애니메이션

- 원화: `heru-guardian-concept-v1.png`
- 입 열린 원화: `heru-guardian-speaking-v1.png`
- 생성 도구: 내장 image_gen, 원화 편집 모드.
- 입/턱 영역만 가장자리를 흐리게 합성하며, 나머지 캐릭터는 원본을 유지한다.
- 녹음 싱크와 독립적으로 원래 모양의 열린 입/닫힌 입을 교체한다. 입술과 수염을 늘리지 않는다.
- 65ms 전환으로 짧게 섞고, 3~5번의 입 움직임 뒤 짧은 쉬는 구간을 넣는다.
- 말하는 화면이 종료되면 루프를 취소하고 즉시 입을 닫는다.
- 음량 분석과 사전 데이터 생성은 필요 없다. 마이크·추가 인터넷 권한 없음.
- 입 위치/마스크는 현재 원화에 맞춘 값이므로 캐릭터 교체 시 다시 조정한다.
- 빌드, 단위 테스트, 린트 통과. 에뮬레이터 경고 재생 화면에서 합성 위치 확인.

## 생성 프롬프트

Edit target: the attached full-body teal Korean goblin guardian illustration. Precise-object-edit for lip animation. KEEP THE EXACT ORIGINAL IMAGE, resolution/aspect ratio, camera, face position, head shape, eye gaze, horns, tusks, hair, lighting, clothing, hands, weapon, background, and all composition invariant. Change ONLY lips and mouth interior: open the mouth naturally as if speaking the Korean vowel '아', moderate opening, a dark anatomically shaded oral cavity, subtle upper tooth edge and muted tongue. Not roaring, not laughing. Lips retain same teal skin material, lower lip lowers just a little into beard region. Maintain two existing outer lower tusks positions and original stern brow. Do not change facial identity or move the face. NO text. This must register perfectly over the source for a mouth-only blend animation. Return the whole original full-length portrait with only the mouth edited.
