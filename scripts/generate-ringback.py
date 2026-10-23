"""Generate the simple outgoing-call signal used by the prototype."""

import math
import struct
import wave
from pathlib import Path


sample_rate = 16_000
duration = 3.0
ring_duration = 1.0
fade_duration = 0.015
output = Path(__file__).resolve().parents[1] / "app/src/main/assets/audio/ringback.wav"

with wave.open(str(output), "wb") as clip:
    clip.setnchannels(1)
    clip.setsampwidth(2)
    clip.setframerate(sample_rate)
    samples = bytearray()
    for index in range(int(sample_rate * duration)):
        time = index / sample_rate
        if time < ring_duration:
            envelope = min(1.0, time / fade_duration, (ring_duration - time) / fade_duration)
            tone = math.sin(2 * math.pi * 440 * time) + math.sin(2 * math.pi * 480 * time)
            amplitude = int(32767 * 0.11 * envelope * tone)
        else:
            amplitude = 0
        samples.extend(struct.pack("<h", amplitude))
    clip.writeframes(samples)

print(output)
