# Engine sounds, synthesised: whistle, exhaust beat and safety valves. Needs numpy and ffmpeg. Run from the repo root.
import subprocess
import wave
import numpy as np

SR = 22050
OUT = 'src/main/resources/assets/railworks/sounds/engine/'
rnd = np.random.default_rng(8)


def lowpass(x, a):
    y = np.zeros_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc += a * (v - acc)
        y[i] = acc
    return y


def env(n, att, rel):
    e = np.ones(n)
    a, r = int(att * SR), int(rel * SR)
    e[:a] = np.linspace(0, 1, a)
    e[n - r:] = np.linspace(1, 0, r)
    return e


def save(name, x):
    x = x / np.max(np.abs(x)) * 0.85
    pcm = (x * 32767).astype(np.int16)
    with wave.open('/tmp/' + name + '.wav', 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', '/tmp/' + name + '.wav', '-c:a', 'libvorbis', '-q:a', '4', OUT + name + '.ogg'], check=True)


t = np.arange(int(1.6 * SR)) / SR
f = 640 * (1 + 0.004 * np.sin(2 * np.pi * 5 * t))
ph = 2 * np.cumsum(f) * np.pi / SR
tone = np.sin(ph) + 0.45 * np.sin(2 * ph) + 0.2 * np.sin(3 * ph) + 0.3 * np.sin(1.26 * ph)
breath = lowpass(rnd.standard_normal(len(t)), 0.3) * 0.25
save('whistle', (tone + breath) * env(len(t), 0.08, 0.3))

n = int(0.32 * SR)
t = np.arange(n) / SR
burst = lowpass(rnd.standard_normal(n), 0.12) * np.exp(-t * 14) + 0.3 * np.sin(2 * np.pi * 70 * t) * np.exp(-t * 20)
save('chuff', burst * env(n, 0.01, 0.05))

n = int(1.5 * SR)
hiss = rnd.standard_normal(n) - lowpass(rnd.standard_normal(n), 0.05)
save('valves', hiss * env(n, 0.15, 0.4) * (1 + 0.1 * np.sin(2 * np.pi * 9 * np.arange(n) / SR)))
