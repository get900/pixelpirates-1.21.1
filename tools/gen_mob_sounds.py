"""Pixel Pirates - procedural mob sound synthesizer.

Run from the repo root:
    pip install numpy scipy        (ffmpeg with libvorbis must be on PATH)
    python tools/gen_mob_sounds.py

Every sound is synthesized from scratch (deterministic seeds) - formant voices, filtered
noise, FM bells, Schroeder reverb - then encoded to MONO Ogg Vorbis (Minecraft only
attenuates mono sounds by distance). Writes assets/pixelpirates/sounds/mob/<mob>/*.ogg and
merges the matching events into sounds.json (music entries are preserved).
Event ids here must match ModSounds.java.
"""
from __future__ import annotations

import json
import subprocess
import tempfile
import wave
from pathlib import Path

import numpy as np
from scipy import signal

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/pixelpirates"
OUT = ASSETS / "sounds/mob"
SR = 44100


# =====================================================================================
# DSP toolkit
# =====================================================================================
def t_(dur):
    return np.arange(int(dur * SR)) / SR


def env(n, attack=0.01, release=0.2, curve=1.0):
    """Attack-then-exponential-release envelope over n samples."""
    e = np.ones(n)
    a = max(1, int(attack * SR))
    e[:a] = np.linspace(0, 1, a)
    r = min(n - a, int(release * SR))
    if r > 0:
        e[n - r:] *= np.linspace(1, 0, r) ** (2 * curve)
    return e


def decay(n, tau):
    return np.exp(-np.arange(n) / (tau * SR))


def phase_of(freq):
    """Integrate a (possibly time-varying) frequency into phase."""
    f = np.broadcast_to(freq, freq.shape if np.ndim(freq) else (1,)).astype(float)
    return 2 * np.pi * np.cumsum(f) / SR


def sine(freq, n=None):
    if np.ndim(freq) == 0:
        freq = np.full(n, freq, float)
    return np.sin(phase_of(freq))


def saw(freq, n=None, harmonics=30):
    """Band-limited sawtooth by additive synthesis (glottal-ish buzz source)."""
    if np.ndim(freq) == 0:
        freq = np.full(n, freq, float)
    ph = phase_of(freq)
    out = np.zeros_like(ph)
    for k in range(1, harmonics + 1):
        mask = (freq * k) < SR / 2.2
        out += mask * np.sin(k * ph) / k
    return out


def noise(n, rng):
    return rng.standard_normal(n)


def bandpass(x, f, q=5.0):
    b, a = signal.iirpeak(min(f, SR / 2.2), q, fs=SR)
    return signal.lfilter(b, a, x)


def lowpass(x, f, order=2):
    b, a = signal.butter(order, min(f, SR / 2.2), "low", fs=SR)
    return signal.lfilter(b, a, x)


def highpass(x, f, order=2):
    b, a = signal.butter(order, f, "high", fs=SR)
    return signal.lfilter(b, a, x)


def sweep_lowpass(x, f0, f1, chunks=48):
    """Time-varying low-pass (filter sweep) done in overlapping chunks."""
    out = np.zeros_like(x)
    n = len(x)
    size = n // chunks + 1
    zi = None
    b, a = signal.butter(2, f0, "low", fs=SR)
    for i in range(chunks):
        s, e = i * size, min(n, (i + 1) * size)
        if s >= e:
            break
        f = f0 * (f1 / f0) ** (i / max(1, chunks - 1))
        b, a = signal.butter(2, min(f, SR / 2.2), "low", fs=SR)
        if zi is None:
            zi = signal.lfilter_zi(b, a) * 0
        out[s:e], zi = signal.lfilter(b, a, x[s:e], zi=zi)
    return out


VOWELS = {  # formant (freq, bandwidth-ish Q, gain)
    "a": [(800, 8, 1.0), (1150, 10, 0.5), (2900, 14, 0.25)],
    "o": [(450, 8, 1.0), (800, 10, 0.45), (2830, 14, 0.15)],
    "u": [(325, 8, 1.0), (700, 10, 0.3), (2530, 14, 0.1)],
    "e": [(400, 8, 1.0), (1600, 12, 0.45), (2700, 14, 0.25)],
    "i": [(270, 8, 1.0), (2100, 12, 0.35), (3000, 14, 0.25)],
    "aw": [(570, 7, 1.0), (840, 9, 0.6), (2410, 12, 0.2)],
}


def formant(src, vowel):
    return sum(g * bandpass(src, f, q) for f, q, g in VOWELS[vowel])


def reverb(x, wet=0.35, size=1.0, damp=3500):
    """Schroeder reverb: 4 parallel damped combs + 2 allpasses."""
    tail = int(SR * 1.6 * size)
    y = np.concatenate([x, np.zeros(tail)])
    acc = np.zeros_like(y)
    for d_ms, g in ((29.7, 0.80), (37.1, 0.78), (41.1, 0.76), (43.7, 0.74)):
        d = int(d_ms * size * SR / 1000)
        b = np.zeros(d + 1); b[0] = 1
        a = np.zeros(d + 1); a[0] = 1; a[d] = -g
        acc += signal.lfilter(b, a, y)
    acc = lowpass(acc, damp)
    for d_ms, g in ((5.0, 0.7), (1.7, 0.7)):
        d = int(d_ms * SR / 1000)
        b = np.zeros(d + 1); b[0] = -g; b[d] = 1
        a = np.zeros(d + 1); a[0] = 1; a[d] = -g
        acc = signal.lfilter(b, a, acc)
    return y * (1 - wet) + acc * wet * 0.25


def place(dst, src, at):
    i = int(at * SR)
    j = min(len(dst), i + len(src))
    if i < len(dst):
        dst[i:j] += src[:j - i]
    return dst


def bubble(rng, f0=None, dur=0.07):
    n = int(dur * SR)
    f0 = f0 or rng.uniform(350, 900)
    f = f0 * (1 + 2.5 * np.linspace(0, 1, n) ** 1.5)
    return sine(f) * decay(n, dur / 3) * env(n, 0.002, 0.01)


def normalize(x, peak=0.89):
    x = x - np.mean(x)
    m = np.max(np.abs(x)) or 1
    return x / m * peak


def trim_tail(x, thresh=0.002):
    idx = np.where(np.abs(x) > thresh)[0]
    return x[: idx[-1] + int(0.02 * SR)] if len(idx) else x


# =====================================================================================
# Sound designs  (each returns a float array; normalized + faded on write)
# =====================================================================================
def voice(notes, vowel, rng, vib=5.5, vib_depth=0.012, breath=0.08, glide=0.06):
    """Sung line: notes = [(freq, dur)], glided, with vibrato and breath noise."""
    total = sum(d for _, d in notes)
    n = int(total * SR)
    f = np.zeros(n)
    pos = 0
    prev = notes[0][0]
    for fr, d in notes:
        k = int(d * SR)
        g = min(k, int(glide * SR))
        seg = np.full(k, fr, float)
        seg[:g] = np.linspace(prev, fr, g)
        f[pos:pos + k] = seg[: n - pos]
        pos += k
        prev = fr
    tt = np.arange(n) / SR
    f *= 1 + vib_depth * np.sin(2 * np.pi * vib * tt) * np.clip(tt / 0.3, 0, 1)
    src = saw(f) + breath * noise(n, rng)
    return formant(src, vowel) * env(n, 0.08, 0.35)


def siren_ambient(rng, v):
    scale = [440, 523.25, 587.33, 659.25, 783.99]
    notes = [(scale[rng.integers(0, 5)] * 0.5, rng.uniform(0.35, 0.55)) for _ in range(3)]
    x = voice(notes, "u", rng, breath=0.12)
    return reverb(x, 0.45, 1.2)


def siren_song(rng, v):
    base = [440, 523.25, 587.33, 659.25, 783.99, 880]
    idx = [2, 3, 5, 4, 3, 1] if v == 0 else [0, 2, 3, 2, 4, 5]
    notes = [(base[i], 0.45 + rng.uniform(-0.05, 0.08)) for i in idx]
    lead = voice(notes, "a", rng, vib=5.2, vib_depth=0.018)
    harm = voice([(f * 1.1892, d) for f, d in notes], "o", rng, vib=5.9, vib_depth=0.015) * 0.45  # minor third
    return reverb(lead + harm, 0.55, 1.5)


def siren_hurt(rng, v):
    n = int(0.35 * SR)
    f = np.linspace(760 + 60 * v, 380, n)
    x = formant(saw(f) * 0.6 + noise(n, rng) * 0.5, "e") * env(n, 0.005, 0.25)
    return reverb(x, 0.25)


def siren_death(rng, v):
    n = int(1.9 * SR)
    tt = np.arange(n) / SR
    f = 880 * (0.25 ** (tt / 1.9)) * (1 + 0.02 * np.sin(2 * np.pi * 6 * tt))
    x = formant(saw(f) + 0.1 * noise(n, rng), "a") * env(n, 0.03, 1.2)
    for k in range(10):
        place(x, bubble(rng) * 0.4, 0.9 + k * 0.09)
    return reverb(x, 0.5, 1.4)


def siren_attack(rng, v):
    n = int(0.45 * SR)
    am = 1 + 0.6 * np.sin(2 * np.pi * 34 * np.arange(n) / SR)
    x = formant(noise(n, rng) * am + 0.4 * saw(np.full(n, 190.0)), "i") * env(n, 0.01, 0.25)
    return highpass(x, 250)


def jelly_ambient(rng, v):
    n = int(0.9 * SR)
    x = 0.25 * sine(220 * (1 + 0.03 * np.sin(2 * np.pi * 3 * np.arange(n) / SR)), n) * env(n, 0.2, 0.4)
    for _ in range(rng.integers(3, 7)):
        place(x, bubble(rng, rng.uniform(500, 1100), rng.uniform(0.05, 0.1)), rng.uniform(0.0, 0.75))
    return reverb(x, 0.3, 0.8)


def jelly_sting(rng, v):
    n = int(0.32 * SR)
    crackle = (rng.random(n) < 0.03).astype(float) * rng.uniform(-1, 1, n)
    buzz = signal.square(2 * np.pi * (58 + 10 * v) * np.arange(n) / SR) * 0.3
    x = highpass(noise(n, rng) * (0.5 + 0.5 * buzz) + crackle * 3, 1800) + 0.5 * sine(np.linspace(2400, 1200, n)) * decay(n, 0.05)
    return x * env(n, 0.002, 0.2)


def jelly_hurt(rng, v):
    n = int(0.3 * SR)
    x = sweep_lowpass(noise(n, rng), 2500, 300) + 0.6 * sine(np.linspace(320 + 40 * v, 110, n))
    return x * env(n, 0.005, 0.2)


def jelly_death(rng, v):
    n = int(1.3 * SR)
    x = sweep_lowpass(noise(n, rng), 1800, 120) * 0.5 * env(n, 0.01, 1.0)
    for k in range(12):
        place(x, bubble(rng, 900 - k * 55, 0.09), k * 0.09 + rng.uniform(0, 0.03))
    return reverb(x, 0.3)


def crackle(n, rng, rate=0.004, f=2200):
    imp = (rng.random(n) < rate) * rng.uniform(-1, 1, n)
    return bandpass(imp * 6, f, 2)


def brute_ambient(rng, v):
    n = int(1.6 * SR)
    tt = np.arange(n) / SR
    am = 0.6 + 0.4 * np.abs(np.sin(2 * np.pi * (1.3 + 0.4 * v) * tt + rng.uniform(0, 3)))
    growl = lowpass(saw(55 + 6 * np.sin(2 * np.pi * 0.7 * tt)) + 0.7 * saw(np.full(n, 82.4)) + 0.4 * noise(n, rng), 380, 3) * am
    return growl * env(n, 0.2, 0.5) + crackle(n, rng) * 0.35


def thud(rng, dur=0.35, f0=95, f1=40):
    n = int(dur * SR)
    return sine(np.linspace(f0, f1, n)) * decay(n, dur / 4) + 0.5 * lowpass(noise(n, rng), 700) * decay(n, dur / 8)


def brute_hurt(rng, v):
    n = int(0.4 * SR)
    x = thud(rng, 0.4, 110, 45) + 0.6 * bandpass(noise(n, rng), 800, 1.5) * decay(n, 0.08)
    return x + 0.5 * lowpass(formant(saw(np.linspace(110, 80, n)), "aw"), 1200) * env(n, 0.01, 0.3)


def brute_death(rng, v):
    n = int(2.6 * SR)
    x = lowpass(saw(np.linspace(70, 35, n)) + noise(n, rng) * 0.5, 300) * env(n, 0.05, 1.8) * 0.6
    for k in range(9):
        place(x, thud(rng, 0.45, rng.uniform(80, 140), 35) * rng.uniform(0.5, 1.0), 0.2 + k * 0.22 + rng.uniform(0, 0.1))
    x += crackle(n, rng, 0.006) * 0.5 * env(n, 0.01, 2.0)
    return reverb(x, 0.3, 1.2, 1500)


def brute_slam(rng, v):
    n = int(2.0 * SR)
    tt = np.arange(n) / SR
    sub = sine(110 * (28 / 110) ** np.clip(tt / 0.6, 0, 1)) * decay(n, 0.45)
    burst = lowpass(noise(n, rng), 900, 3) * decay(n, 0.12) * 1.4
    x = sub * 1.2 + burst + crackle(n, rng, 0.01, 1800) * decay(n, 0.6) * 0.8
    return reverb(x, 0.35, 1.3, 1200)


def brute_step(rng, v):
    n = int(0.3 * SR)
    return thud(rng, 0.3, 70 + 10 * v, 35) + 0.3 * bandpass(noise(n, rng), 600, 1.5) * decay(n, 0.05)


def brute_roar(rng, v):
    n = int(1.1 * SR)
    tt = np.arange(n) / SR
    src = saw(70 + 20 * np.clip(tt / 0.5, 0, 1)) * (1 + 0.3 * np.sin(2 * np.pi * 23 * tt)) + 0.6 * noise(n, rng)
    return reverb(lowpass(formant(src, "aw"), 2000) * env(n, 0.25, 0.4), 0.3, 1.1, 1800)


def creak(rng, dur, r0, r1, f=900):
    n = int(dur * SR)
    rate = np.linspace(r0, r1, n) * (1 + 0.15 * np.sin(2 * np.pi * 3 * np.arange(n) / SR))
    ph = np.cumsum(rate) / SR
    pulses = (np.diff(np.floor(ph), prepend=0) > 0).astype(float)
    return (bandpass(pulses, f, 12) + 0.6 * bandpass(pulses, f * 2.1, 14)) * env(n, 0.05, 0.2)


def clack(rng, bright=1.0):
    n = int(0.18 * SR)
    imp = np.zeros(n); imp[0] = 1; imp[1:40] = rng.uniform(-0.5, 0.5, 39) * np.linspace(1, 0, 39)
    return (bandpass(imp, 260, 6) * 2 + bandpass(imp, 720 * bright, 8) + 0.6 * bandpass(imp, 1600 * bright, 10)) * 6


def mimic_ambient(rng, v):
    n = int(1.1 * SR)
    x = np.zeros(n)
    place(x, creak(rng, 0.8, 22 + 6 * v, 55, 850 + 150 * v), 0.05)
    growl = lowpass(saw(np.full(int(0.6 * SR), 95.0)) * (1 + 0.5 * np.sin(2 * np.pi * 28 * np.arange(int(0.6 * SR)) / SR)), 600)
    return place(x, growl * env(len(growl), 0.1, 0.3) * 0.5, 0.45)


def mimic_awaken(rng, v):
    n = int(1.3 * SR)
    x = np.zeros(n)
    place(x, creak(rng, 0.5, 15, 70, 700), 0.0)
    m = int(0.8 * SR)
    snarl = formant(noise(m, rng) * (1 + 0.8 * np.sin(2 * np.pi * 31 * np.arange(m) / SR)) + 0.5 * saw(np.linspace(120, 170, m)), "aw")
    place(x, snarl * env(m, 0.08, 0.3) * 0.8, 0.35)
    place(x, clack(rng) * 0.9, 1.1)
    return reverb(x, 0.15)


def mimic_bite(rng, v):
    n = int(0.35 * SR)
    x = np.zeros(n)
    place(x, clack(rng, 1.0 + 0.15 * v), 0.0)
    place(x, clack(rng, 1.3) * 0.6, 0.07)
    return x


def mimic_hurt(rng, v):
    n = int(0.35 * SR)
    x = np.zeros(n)
    place(x, clack(rng, 0.8) * 0.7, 0)
    m = int(0.25 * SR)
    place(x, formant(saw(np.linspace(520 + 60 * v, 340, m)), "e") * env(m, 0.005, 0.18) * 0.6, 0.03)
    return x


def mimic_death(rng, v):
    n = int(1.6 * SR)
    x = np.zeros(n)
    for _ in range(40):
        at = rng.uniform(0, 1.1) ** 1.6
        place(x, clack(rng, rng.uniform(0.9, 2.2)) * rng.uniform(0.15, 0.6) * (1 - at / 1.3), at)
    x += highpass(noise(n, rng), 1500) * 0.15 * decay(n, 0.3)
    place(x, clack(rng, 0.7) * 1.2, 1.35)                    # the lid slams shut one last time
    return reverb(x, 0.2)


def angler_ambient(rng, v):
    n = int(2.6 * SR)
    tt = np.arange(n) / SR
    f = 85 + 30 * np.sin(np.pi * tt / 2.6) + 8 * v + 3 * np.sin(2 * np.pi * 0.8 * tt)
    x = (sine(f) + 0.45 * sine(2 * f) + 0.2 * sine(3.02 * f)) * env(n, 0.6, 1.0)
    x = lowpass(x, 600)
    for _ in range(4):
        place(x, bubble(rng, rng.uniform(300, 600), 0.08) * 0.3, rng.uniform(0.3, 2.2))
    return reverb(x, 0.55, 1.8, 1200)


def angler_lure(rng, v):
    n = int(1.4 * SR)
    x = np.zeros(n)
    for k, fr in enumerate((880, 1108.7, 1318.5, 1760)):
        m = int(1.1 * SR)
        tt = np.arange(m) / SR
        mod = 2.1 * fr * (1 + 0.5 * np.exp(-tt * 6))
        bell = np.sin(2 * np.pi * fr * tt + 1.4 * np.exp(-tt * 3) * np.sin(2 * np.pi * mod * tt)) * decay(m, 0.35)
        place(x, bell * 0.5, k * 0.07)
    return reverb(x, 0.5, 1.3, 6000)


def angler_bite(rng, v):
    n = int(0.5 * SR)
    x = lowpass(thud(rng, 0.25, 160, 60), 900)
    x = np.concatenate([x, np.zeros(n - len(x))])
    place(x, lowpass(clack(rng, 1.4), 1500) * 0.6, 0.02)
    for _ in range(6):
        place(x, bubble(rng, rng.uniform(400, 900), 0.06) * 0.35, rng.uniform(0.05, 0.3))
    return x


def angler_hurt(rng, v):
    n = int(0.45 * SR)
    src = saw(np.linspace(150 + 20 * v, 90, n)) * (1 + 0.7 * np.sin(2 * np.pi * 18 * np.arange(n) / SR))
    x = lowpass(formant(src + 0.4 * noise(n, rng), "o"), 1400) * env(n, 0.01, 0.3)
    for _ in range(3):
        place(x, bubble(rng) * 0.3, rng.uniform(0, 0.3))
    return x


def angler_death(rng, v):
    n = int(2.6 * SR)
    tt = np.arange(n) / SR
    f = 110 * (0.35 ** (tt / 2.6))
    x = lowpass(sine(f) + 0.5 * saw(f) * 0.3, 500) * env(n, 0.05, 1.8)
    for k in range(18):
        place(x, bubble(rng, rng.uniform(250, 700), 0.1) * 0.35, 0.3 + k * 0.11 + rng.uniform(0, 0.05))
    return reverb(x, 0.5, 1.8, 1100)


# =====================================================================================
# Existing mobs: shark, crabs, castaway, monkey, merchant, pirate crew, captain
# =====================================================================================
def babble(rng, syllables, pitch, contour=0.0, vowels=("a", "o", "e", "u", "aw"), rate=0.14, rasp=0.15, breath=0.1):
    """Speech-like gibberish: vowel syllables with formants, consonant bursts, pitch contour.
    contour > 0 rises over the phrase (question), < 0 falls (statement/growl)."""
    parts = []
    for i in range(syllables):
        d = rate * rng.uniform(0.8, 1.5)
        n = int(d * SR)
        prog = i / max(1, syllables - 1)
        f0 = pitch * (1 + contour * prog) * rng.uniform(0.93, 1.07)
        f = np.linspace(f0 * 1.04, f0 * 0.97, n) * (1 + 0.01 * np.sin(2 * np.pi * 6 * np.arange(n) / SR))
        src = saw(f) * (1 + rasp * noise(n, rng)) + breath * noise(n, rng)
        syl = formant(src, vowels[rng.integers(0, len(vowels))]) * env(n, 0.015, d * 0.5)
        cons = int(0.03 * SR)
        burst = highpass(noise(cons, rng), rng.choice([1500, 3000, 5000])) * env(cons, 0.002, 0.02) * 0.4
        parts += [burst, syl, np.zeros(int(rng.uniform(0.01, 0.05) * SR))]
    return np.concatenate(parts)


def swish(rng, dur=0.5, f0=300, f1=1400):
    n = int(dur * SR)
    return sweep_lowpass(noise(n, rng), f0, f1) * np.sin(np.pi * np.linspace(0, 1, n)) ** 2


def splash(rng, dur=0.6):
    n = int(dur * SR)
    x = highpass(noise(n, rng), 600) * decay(n, dur / 5)
    for _ in range(8):
        place(x, bubble(rng, rng.uniform(400, 1200), 0.05) * 0.5, rng.uniform(0.05, dur * 0.8))
    return x


def clicks(rng, dur, rate, f=3200, q=6):
    n = int(dur * SR)
    imp = (rng.random(n) < rate / SR) * rng.uniform(0.5, 1, n)
    return bandpass(imp, f, q) * 12


def sizzle(rng, dur):
    n = int(dur * SR)
    return highpass(noise(n, rng), 3500) * (0.4 + 0.6 * rng.random(n) ** 8) * 0.6


def shark_ambient(rng, v):
    x = swish(rng, 1.2, 200, 900 + 200 * v) * 0.8
    for _ in range(3):
        place(x, bubble(rng, rng.uniform(300, 600), 0.08) * 0.3, rng.uniform(0.1, 1.0))
    return lowpass(x, 1800)


def shark_bite(rng, v):
    n = int(0.45 * SR)
    x = np.zeros(n)
    place(x, lowpass(clack(rng, 0.6 + 0.1 * v), 2500) * 1.2, 0.0)
    place(x, lowpass(clack(rng, 0.9), 2500) * 0.6, 0.06)
    place(x, splash(rng, 0.35) * 0.5, 0.02)
    return x


def shark_hurt(rng, v):
    n = int(0.5 * SR)
    x = splash(rng, 0.5) * 0.8
    thrash = lowpass(noise(n, rng), 500) * (0.5 + 0.5 * np.sin(2 * np.pi * 9 * np.arange(n) / SR)) * decay(n, 0.2)
    return x + thrash + 0.4 * lowpass(saw(np.linspace(90 + 15 * v, 60, n)), 400) * env(n, 0.01, 0.3)


def shark_death(rng, v):
    n = int(1.6 * SR)
    x = lowpass(noise(n, rng), 450) * (0.5 + 0.5 * np.sin(2 * np.pi * np.linspace(10, 2, n) * np.arange(n) / SR)) * env(n, 0.01, 1.2)
    for k in range(14):
        place(x, bubble(rng, 700 - k * 30, 0.08) * 0.4, 0.3 + k * 0.08)
    return reverb(x, 0.3)


def shark_breach(rng, v):
    n = int(1.0 * SR)
    x = np.zeros(n)
    place(x, swish(rng, 0.35, 400, 3000) * 0.8, 0)
    place(x, splash(rng, 0.7), 0.3)
    return x


def chest_crab_ambient(rng, v):
    x = clicks(rng, 0.6, 35 + 10 * v, 3000 + 400 * v)
    return x + 0.3 * clicks(rng, 0.6, 12, 1400)


def chest_crab_hurt(rng, v):
    n = int(0.3 * SR)
    x = np.zeros(n)
    place(x, clack(rng, 1.8) * 0.5, 0)
    squeak = formant(saw(np.linspace(900 + 100 * v, 1300, int(0.18 * SR))), "i") * env(int(0.18 * SR), 0.005, 0.1)
    return place(x, squeak * 0.7, 0.03)


def chest_crab_death(rng, v):
    n = int(0.9 * SR)
    x = clicks(rng, 0.9, 25, 2600) * np.linspace(1, 0.1, n)
    place(x, clack(rng, 1.1) * 0.8, 0.6)                    # lid shuts for good
    return x


def chest_crab_step(rng, v):
    return clicks(rng, 0.08, 60, 3600 + 300 * v) + 0.2 * clicks(rng, 0.08, 40, 1800)


def chest_crab_hide(rng, v):
    n = int(0.4 * SR)
    x = np.zeros(n)
    place(x, creak(rng, 0.2, 40, 20, 1200) * 0.5, 0)
    place(x, clack(rng, 1.0) * 1.0, 0.17)
    return x


def coin_jingle(rng, v):
    n = int(0.5 * SR)
    x = np.zeros(n)
    for k in range(4 + v):
        m = int(0.3 * SR)
        tt = np.arange(m) / SR
        fr = rng.uniform(3200, 5200)
        ring = (np.sin(2 * np.pi * fr * tt) + 0.5 * np.sin(2 * np.pi * fr * 2.76 * tt)) * decay(m, 0.06)
        place(x, ring * 0.4, k * 0.045 + rng.uniform(0, 0.02))
    return x


def lava_crab_ambient(rng, v):
    n = int(0.8 * SR)
    return clicks(rng, 0.8, 25, 2400 + 300 * v) + sizzle(rng, 0.8) * env(n, 0.1, 0.3) + 0.3 * lowpass(noise(n, rng), 200) * env(n, 0.2, 0.3)


def lava_crab_hurt(rng, v):
    n = int(0.35 * SR)
    x = sizzle(rng, 0.35) * env(n, 0.005, 0.25) * 1.5
    return place(x, clack(rng, 1.5) * 0.5, 0.0)


def lava_crab_death(rng, v):
    n = int(1.2 * SR)
    x = sizzle(rng, 1.2) * env(n, 0.01, 1.0) * 1.3 + clicks(rng, 1.2, 15, 2000) * np.linspace(1, 0, n)
    place(x, clack(rng, 0.7) * 0.8, 0.8)                     # shell cracks
    return x


def lava_crab_step(rng, v):
    n = int(0.1 * SR)
    return clicks(rng, 0.1, 50, 2800) + sizzle(rng, 0.1) * 0.4 * env(n, 0.005, 0.05)


def castaway_ambient(rng, v):
    x = babble(rng, rng.integers(3, 6), 125 + 10 * v, contour=rng.choice([-0.25, 0.3]), rasp=0.2)
    return reverb(x, 0.1)


def castaway_hurt(rng, v):
    return babble(rng, 1, 170 + 15 * v, vowels=("o", "u"), rate=0.18, rasp=0.3, breath=0.25)


def castaway_death(rng, v):
    n = int(1.2 * SR)
    return formant(saw(np.linspace(160, 80, n)) * (1 + 0.3 * noise(n, rng)) + 0.2 * noise(n, rng), "o") * env(n, 0.05, 0.9)


def castaway_drink(rng, v):
    n = int(0.9 * SR)
    x = np.zeros(n)
    for k in range(3):
        m = int(0.14 * SR)
        glug = lowpass(sine(np.linspace(180, 420, m)) * env(m, 0.005, 0.08) + 0.3 * noise(m, rng) * decay(m, 0.03), 900)
        place(x, glug, 0.1 + k * 0.22)
    return place(x, formant(noise(int(0.2 * SR), rng), "a") * env(int(0.2 * SR), 0.02, 0.15) * 0.4, 0.72)  # "ahh"


def throw_whoosh(rng, v):
    return swish(rng, 0.35, 700, 5000 + 800 * v)


def monkey_ambient(rng, v):
    """Hoots rising to a shriek, detuned with an echo - it's cursed."""
    parts = []
    for i in range(rng.integers(3, 6)):
        d = 0.12 + 0.03 * i
        n = int(d * SR)
        f0 = 380 * (1.15 ** i)
        src = saw(np.linspace(f0 * 0.8, f0 * 1.25, n))
        parts += [formant(src, "u" if i < 2 else "a") * env(n, 0.01, d * 0.6), np.zeros(int(0.04 * SR))]
    x = np.concatenate(parts)
    x = x + 0.5 * np.concatenate([np.zeros(int(0.012 * SR)), x[:-int(0.012 * SR)]])   # detuned double
    return reverb(x, 0.35, 0.9)


def monkey_hurt(rng, v):
    n = int(0.3 * SR)
    return formant(saw(np.linspace(900 + 100 * v, 1400, n)) + 0.3 * noise(n, rng), "i") * env(n, 0.005, 0.2)


def monkey_death(rng, v):
    n = int(1.0 * SR)
    x = formant(saw(np.linspace(1300, 300, n)) + 0.3 * noise(n, rng), "a") * env(n, 0.01, 0.7)
    return reverb(x, 0.45, 1.2)


def monkey_step(rng, v):
    n = int(0.06 * SR)
    return lowpass(noise(n, rng), 1200) * decay(n, 0.01)


def merchant_ambient(rng, v):
    x = babble(rng, rng.integers(2, 5), 105, contour=0.35 if v == 1 else -0.15, vowels=("o", "u", "aw", "e"), rasp=0.1, breath=0.35)
    x = lowpass(x, 2200)                                        # muffled under the hood
    n = int(0.35 * SR)
    rustle = bandpass(noise(n, rng), 3500, 1.5) * (rng.random(n) ** 3) * env(n, 0.02, 0.2) * 0.5
    return reverb(place(np.concatenate([x, np.zeros(n)]), rustle, len(x) / SR - 0.05), 0.25)


def merchant_trade(rng, v):
    n = int(0.6 * SR)
    rustle = bandpass(noise(n, rng), 3000, 1.2) * (rng.random(n) ** 3) * env(n, 0.02, 0.3)
    return rustle + np.pad(coin_jingle(rng, 0), (0, max(0, n - int(0.5 * SR))))[:n] * 0.6


def merchant_hurt(rng, v):
    return lowpass(babble(rng, 1, 140 + 10 * v, vowels=("u", "o"), rate=0.16, breath=0.4), 2400)


def merchant_death(rng, v):
    n = int(1.4 * SR)
    x = lowpass(formant(saw(np.linspace(130, 70, n)) + 0.5 * noise(n, rng), "u"), 2000) * env(n, 0.05, 1.0)
    return reverb(x, 0.4, 1.3)


def pirate_ambient(rng, v):
    """Gruff "arr"/"yo-ho" style barks: low pitch, rasp, falling contour."""
    x = babble(rng, rng.integers(2, 4), 105 + 8 * v, contour=-0.25, vowels=("a", "aw", "o"), rasp=0.35)
    return lowpass(x, 3000)


def pirate_hurt(rng, v):
    return babble(rng, 1, 140 + 12 * v, vowels=("a", "u"), rate=0.15, rasp=0.45, breath=0.2)


def pirate_death(rng, v):
    n = int(1.1 * SR)
    return formant(saw(np.linspace(140, 70, n)) * (1 + 0.4 * noise(n, rng)), "aw") * env(n, 0.03, 0.8)


def captain_ambient(rng, v):
    """Deeper, longer, commanding - with a rising shout at the end."""
    x = babble(rng, rng.integers(4, 7), 88 + 6 * v, contour=0.35 if v == 2 else -0.1, vowels=("a", "aw", "o", "e"), rate=0.17, rasp=0.3)
    return reverb(lowpass(x, 3200), 0.15)


def captain_hurt(rng, v):
    return babble(rng, 1, 115 + 10 * v, vowels=("aw",), rate=0.2, rasp=0.5, breath=0.15)


def captain_death(rng, v):
    n = int(1.6 * SR)
    x = formant(saw(np.linspace(120, 55, n)) * (1 + 0.4 * noise(n, rng)), "a") * env(n, 0.03, 1.2)
    return reverb(x, 0.3, 1.2)


# =====================================================================================
# Sound FAMILIES for the data-driven roster (MobSpecs). Each family renders the five event
# kinds; `pitch` scales the voice (0.5 = colossal, 1.4 = tiny).
# =====================================================================================
def mx(*xs):
    """Mix signals of different lengths (reverb tails differ) by zero-padding to the longest."""
    n = max(len(x) for x in xs)
    return sum(np.pad(x, (0, n - len(x))) for x in xs)


def fam_voice(kind, rng, v, pitch):
    base = 115 * pitch
    if kind == "ambient":
        return lowpass(babble(rng, rng.integers(2, 5), base, contour=rng.choice([-0.25, 0.25]), rasp=0.3), 3200)
    if kind == "hurt":
        return babble(rng, 1, base * 1.3, vowels=("a", "u"), rate=0.15, rasp=0.45, breath=0.2)
    if kind == "death":
        n = int(1.2 * SR)
        return formant(saw(np.linspace(base * 1.2, base * 0.6, n)) * (1 + 0.4 * noise(n, rng)), "aw") * env(n, 0.03, 0.9)
    if kind == "attack":
        return babble(rng, 1, base * 1.1, vowels=("a",), rate=0.12, rasp=0.5)
    return reverb(babble(rng, 3, base * 0.9, contour=0.4, vowels=("a", "aw"), rate=0.2, rasp=0.4), 0.3, 1.2)   # war cry


def fam_undead(kind, rng, v, pitch):
    rattle = lambda d: clicks(rng, d, 40, 2600 * pitch, 5) * 0.8
    if kind == "ambient":
        n = int(1.3 * SR)
        moan = lowpass(formant(saw(np.linspace(95 * pitch, 80 * pitch, n)) * (1 + 0.3 * noise(n, rng)), "o"), 1500) * env(n, 0.3, 0.6)
        return moan + rattle(1.3) * 0.5
    if kind == "hurt":
        return rattle(0.3) + np.pad(clack(rng, 1.6 * pitch), (0, int(0.3 * SR) - int(0.18 * SR)))[:int(0.3 * SR)]
    if kind == "death":
        n = int(1.4 * SR)
        x = rattle(1.4) * np.linspace(1, 0.1, n)
        for k in range(6):
            place(x, clack(rng, rng.uniform(1.0, 2.0)) * 0.6, k * 0.12 + rng.uniform(0, 0.05))
        return reverb(x, 0.25)
    if kind == "attack":
        return swish(rng, 0.25, 800, 4000) + rattle(0.25) * 0.4
    n = int(1.5 * SR)
    return reverb(lowpass(formant(saw(np.linspace(70 * pitch, 110 * pitch, n)) * (1 + 0.5 * noise(n, rng)), "aw"), 1800) * env(n, 0.2, 0.5), 0.45, 1.4)


def fam_ghost(kind, rng, v, pitch):
    def wail(dur, f0, f1, wet=0.6):
        n = int(dur * SR)
        tt = np.arange(n) / SR
        f = np.linspace(f0, f1, n) * pitch * (1 + 0.03 * np.sin(2 * np.pi * 5 * tt))
        src = 0.6 * sine(f) + 0.4 * bandpass(noise(n, rng), float(np.mean(f)) * 2, 3)
        return reverb(src * env(n, dur * 0.3, dur * 0.4), wet, 1.6)
    if kind == "ambient": return wail(1.6, 380, 300)
    if kind == "hurt": return wail(0.4, 700, 500, 0.3)
    if kind == "death": return wail(2.0, 600, 150, 0.7)
    if kind == "attack": return mx(highpass(swish(rng, 0.3, 1200, 6000), 800), wail(0.3, 500, 700, 0.2) * 0.5)
    return wail(1.4, 200, 600, 0.7)


def fam_fire(kind, rng, v, pitch):
    roar = lambda d: lowpass(noise(int(d * SR), rng), 900 * pitch) * env(int(d * SR), d * 0.2, d * 0.5)
    if kind == "ambient": return crackle(int(1.2 * SR), rng, 0.006, 2400) + roar(1.2) * 0.4
    if kind == "hurt": return sizzle(rng, 0.35) * 1.4 + roar(0.35) * 0.5
    if kind == "death":
        n = int(1.5 * SR)
        return (sizzle(rng, 1.5) * env(n, 0.01, 1.3) + roar(1.5) * 0.6 + crackle(n, rng, 0.004) * 0.5)
    if kind == "attack": return swish(rng, 0.35, 400, 3000) * 0.8 + roar(0.35)
    n = int(1.3 * SR)
    return reverb(roar(1.3) * 1.3 + crackle(n, rng, 0.012, 1800) * 0.8, 0.3)


def fam_stone(kind, rng, v, pitch):
    if kind == "ambient":
        n = int(1.4 * SR)
        return lowpass(noise(n, rng), 250 * pitch) * (0.6 + 0.4 * np.abs(np.sin(2 * np.pi * 1.5 * np.arange(n) / SR))) * env(n, 0.2, 0.4)
    if kind == "hurt": return thud(rng, 0.35, 120 * pitch, 50) + bandpass(noise(int(0.35 * SR), rng), 900, 1.5) * decay(int(0.35 * SR), 0.06) * 0.6
    if kind == "death":
        n = int(2.4 * SR)
        x = lowpass(noise(n, rng), 300) * env(n, 0.05, 1.8) * 0.5
        for k in range(8):
            place(x, thud(rng, 0.4, rng.uniform(70, 130) * pitch, 35), 0.1 + k * 0.25)
        return reverb(x, 0.25, 1.2, 1500)
    if kind == "attack": return mx(thud(rng, 0.4, 90 * pitch, 40), swish(rng, 0.3, 200, 1200) * 0.5)
    return brute_slam(rng, v)


def fam_beast(kind, rng, v, pitch):
    def growl(dur, f0, f1, vowel="aw", amt=0.5):
        n = int(dur * SR)
        tt = np.arange(n) / SR
        src = saw(np.linspace(f0, f1, n) * pitch) * (1 + amt * np.sin(2 * np.pi * 27 * tt)) + 0.5 * noise(n, rng)
        return lowpass(formant(src, vowel), 2400) * env(n, 0.05, dur * 0.4)
    if kind == "ambient": return reverb(growl(1.2, 70, 60, "o"), 0.3, 1.2, 1500)
    if kind == "hurt": return growl(0.35, 160, 120, "a", 0.8)
    if kind == "death": return reverb(growl(1.8, 120, 45, "aw"), 0.4, 1.4, 1400)
    if kind == "attack": return np.pad(clack(rng, 0.7), (0, int(0.2 * SR)))[:int(0.35 * SR)] + growl(0.35, 130, 100, "a") * 0.6
    return reverb(growl(1.6, 60, 110, "aw", 0.7), 0.45, 1.6, 1800)     # roar


def fam_insect(kind, rng, v, pitch):
    if kind == "ambient": return clicks(rng, 0.8, 30, 2800 * pitch) + 0.3 * clicks(rng, 0.8, 12, 1300 * pitch)
    if kind == "hurt": return np.pad(clack(rng, 1.8 * pitch), (0, 4000))[:int(0.25 * SR)] + clicks(rng, 0.25, 80, 3500)
    if kind == "death":
        n = int(1.0 * SR)
        return clicks(rng, 1.0, 45, 2400 * pitch) * np.linspace(1, 0.05, n) + np.pad(clack(rng, 0.9), (0, n))[:n] * 0.6
    if kind == "attack": return np.pad(clack(rng, 1.2 * pitch), (0, 6000))[:int(0.3 * SR)] + swish(rng, 0.3, 1500, 6000) * 0.4
    n = int(0.9 * SR)
    return highpass(noise(n, rng), 2000) * (0.5 + 0.5 * np.sin(2 * np.pi * 45 * np.arange(n) / SR)) * env(n, 0.05, 0.4)   # hiss/rattle


def fam_sea(kind, rng, v, pitch):
    def moan(dur, f0, f1, wet=0.55):
        n = int(dur * SR)
        tt = np.arange(n) / SR
        f = np.linspace(f0, f1, n) * pitch + 6 * np.sin(2 * np.pi * 0.7 * tt)
        x = lowpass(sine(f) + 0.4 * sine(2 * f) + 0.15 * sine(3.01 * f), 700) * env(n, dur * 0.3, dur * 0.4)
        for _ in range(3):
            place(x, bubble(rng) * 0.25, rng.uniform(0, dur * 0.8))
        return reverb(x, wet, 1.8, 1200)
    if kind == "ambient": return moan(2.2, 110, 150)
    if kind == "hurt": return mx(moan(0.5, 220, 160, 0.2), splash(rng, 0.4) * 0.5)
    if kind == "death": return moan(2.4, 160, 60, 0.6)
    if kind == "attack": return splash(rng, 0.5) + np.pad(lowpass(clack(rng, 0.8), 1600), (0, int(0.5 * SR)))[:int(0.5 * SR)]
    return moan(1.8, 90, 220, 0.7)


def fam_squish(kind, rng, v, pitch):
    def slurp(dur, f0, f1):
        n = int(dur * SR)
        return (sweep_lowpass(noise(n, rng), 2500 * pitch, 300 * pitch) + 0.5 * sine(np.linspace(f0 * pitch, f1 * pitch, n))) * env(n, 0.02, dur * 0.6)
    if kind == "ambient":
        x = slurp(0.9, 180, 120) * 0.6
        for _ in range(4):
            place(x, bubble(rng) * 0.4, rng.uniform(0, 0.7))
        return x
    if kind == "hurt": return slurp(0.3, 320, 140)
    if kind == "death":
        x = slurp(1.3, 260, 60)
        for k in range(10):
            place(x, bubble(rng, 800 - k * 50, 0.09) * 0.4, k * 0.1)
        return x
    if kind == "attack": return slurp(0.35, 140, 380) + np.pad(clack(rng, 0.6), (0, int(0.35 * SR)))[:int(0.35 * SR)] * 0.5
    return reverb(slurp(1.2, 90, 260) * 1.2, 0.4)


def fam_shadow(kind, rng, v, pitch):
    def hum(dur, f0, beats=0.0, wet=0.5):
        n = int(dur * SR)
        tt = np.arange(n) / SR
        x = (sine(np.full(n, f0 * pitch)) + sine(np.full(n, f0 * pitch * 1.013)) + 0.4 * bandpass(noise(n, rng), f0 * pitch * 4, 6))
        if beats:
            x *= 0.4 + 0.6 * np.clip(np.sin(2 * np.pi * beats * tt), 0, 1) ** 4   # heartbeat pulse
        return reverb(x * env(n, dur * 0.3, dur * 0.4), wet, 1.6)
    if kind == "ambient": return hum(1.8, 70, beats=1.2 if pitch < 0.8 else 0)
    if kind == "hurt": return mx(hum(0.35, 220, wet=0.2), highpass(noise(int(0.35 * SR), rng), 3000) * 0.3 * env(int(0.35 * SR), 0.005, 0.2))
    if kind == "death":
        x = hum(2.2, 120, wet=0.7)
        return x * np.linspace(1, 0, len(x))
    if kind == "attack": return mx(highpass(swish(rng, 0.3, 1500, 7000), 1000), hum(0.3, 160, wet=0.1) * 0.5)
    return mx(hum(1.5, 55, wet=0.7), reverb(highpass(noise(int(1.5 * SR), rng), 2500) * env(int(1.5 * SR), 0.4, 0.6) * 0.3, 0.6))


FAMILIES = {"voice": fam_voice, "undead": fam_undead, "ghost": fam_ghost, "fire": fam_fire, "stone": fam_stone,
            "beast": fam_beast, "insect": fam_insect, "sea": fam_sea, "squish": fam_squish, "shadow": fam_shadow}

# mob id -> (family, pitch, display name) - must cover every id in MobSpecs.java
ROSTER = {
    "raft_pirate": ("voice", 1.1, "Raft Pirate"), "captain_rackham": ("voice", 0.8, "Captain Rackham"),
    "bloodfin": ("beast", 0.7, "Bloodfin"), "sea_serpent": ("beast", 0.8, "Tidecoil Serpent"),
    "kraken_tentacle": ("squish", 1.0, "Kraken Tentacle"), "kraken": ("squish", 0.55, "The Kraken"), "kraken_arm": ("squish", 0.7, "Kraken Arm"),
    "reefback_fish": ("sea", 1.0, "Reefback"), "void_squid": ("squish", 1.2, "Void Squid"),
    "molten_warlord": ("stone", 0.7, "Molten Warlord"), "obsidian_golem": ("stone", 0.9, "Obsidian Golem"),
    "flame_sprite": ("fire", 1.4, "Flame Sprite"), "ember_wraith": ("fire", 1.0, "Ember Wraith"),
    "lava_scorpion": ("insect", 0.9, "Lava Scorpion"), "fire_pirate": ("voice", 1.0, "Fire Pirate"),
    "skeleton_pirate": ("undead", 1.1, "Skeleton Pirate"), "ghost_shark": ("ghost", 0.9, "Ghost Shark"),
    "chained_revenant": ("undead", 0.6, "Chained Revenant"), "drowned_hands": ("undead", 1.0, "Drowned Hands"),
    "trident_skeleton": ("undead", 1.2, "Trident Skeleton"), "ghost_captain": ("ghost", 0.7, "Ghost Captain"),
    "phantom_pirate": ("ghost", 1.1, "Phantom Pirate"), "abyss_crab": ("insect", 0.8, "Abyss Crab"),
    "abyssal_king": ("voice", 0.7, "Abyssal King"), "brain_fish": ("shadow", 1.2, "Brain Fish"),
    "abyssal_centipede": ("insect", 1.1, "Abyssal Centipede"), "shadow": ("shadow", 0.9, "Shadow"),
    "coral_whale": ("sea", 0.6, "Coral Whale"), "luminous_isopod": ("insect", 1.4, "Luminous Isopod"),
    "deep_lurker": ("beast", 1.0, "Deep Lurker"), "corrupted_diver": ("undead", 0.9, "Corrupted Diver"), "tide_warden": ("undead", 0.75, "Tide Warden"),
    "abyss_eel": ("beast", 1.3, "Abyss Eel"), "crystal_golem": ("stone", 1.2, "Crystal Golem"),
    "drowned_sailor": ("undead", 1.0, "Drowned Sailor"), "jelly_skull": ("squish", 1.3, "Jelly Skull"),
    "anemone_eye": ("squish", 1.1, "Anemone Eye"), "void_manta": ("sea", 1.2, "Void Manta"),
    "abyssal_heart": ("shadow", 0.6, "Abyssal Heart"), "lantern_squid": ("squish", 1.1, "Lantern Squid"),
    "leviathan": ("beast", 0.5, "The Leviathan"),
}
KIND_WORDS = {"ambient": "stirs", "hurt": "hurts", "death": "dies", "attack": "attacks", "special": "unleashes power"}
KIND_VARIANTS = {"ambient": 2, "hurt": 2, "death": 1, "attack": 1, "special": 1}


# event id -> (synth fn, variants, subtitle)
SOUNDS = {
    "entity.siren.ambient": (siren_ambient, 3, "Siren hums"),
    "entity.siren.song": (siren_song, 2, "Siren sings"),
    "entity.siren.hurt": (siren_hurt, 2, "Siren hurts"),
    "entity.siren.death": (siren_death, 1, "Siren dies"),
    "entity.siren.attack": (siren_attack, 2, "Siren hisses"),
    "entity.coral_jelly.ambient": (jelly_ambient, 3, "Coral Jelly burbles"),
    "entity.coral_jelly.sting": (jelly_sting, 2, "Coral Jelly stings"),
    "entity.coral_jelly.hurt": (jelly_hurt, 2, "Coral Jelly hurts"),
    "entity.coral_jelly.death": (jelly_death, 1, "Coral Jelly dies"),
    "entity.magma_brute.ambient": (brute_ambient, 3, "Magma Brute rumbles"),
    "entity.magma_brute.hurt": (brute_hurt, 2, "Magma Brute hurts"),
    "entity.magma_brute.death": (brute_death, 1, "Magma Brute crumbles"),
    "entity.magma_brute.slam": (brute_slam, 2, "Magma Brute slams"),
    "entity.magma_brute.step": (brute_step, 3, "Magma Brute stomps"),
    "entity.magma_brute.roar": (brute_roar, 2, "Magma Brute roars"),
    "entity.mimic.ambient": (mimic_ambient, 2, "Mimic creaks"),
    "entity.mimic.awaken": (mimic_awaken, 1, "Mimic awakens"),
    "entity.mimic.bite": (mimic_bite, 2, "Mimic bites"),
    "entity.mimic.hurt": (mimic_hurt, 2, "Mimic hurts"),
    "entity.mimic.death": (mimic_death, 1, "Mimic splinters"),
    "entity.abyssal_angler.ambient": (angler_ambient, 3, "Abyssal Angler moans"),
    "entity.abyssal_angler.lure": (angler_lure, 1, "Abyssal Angler's lure flares"),
    "entity.abyssal_angler.bite": (angler_bite, 2, "Abyssal Angler bites"),
    "entity.abyssal_angler.hurt": (angler_hurt, 2, "Abyssal Angler hurts"),
    "entity.abyssal_angler.death": (angler_death, 1, "Abyssal Angler dies"),
    "entity.shark.ambient": (shark_ambient, 2, "Shark swims"),
    "entity.shark.bite": (shark_bite, 2, "Shark bites"),
    "entity.shark.hurt": (shark_hurt, 2, "Shark thrashes"),
    "entity.shark.death": (shark_death, 1, "Shark dies"),
    "entity.shark.breach": (shark_breach, 1, "Shark breaches"),
    "entity.chest_crab.ambient": (chest_crab_ambient, 2, "Chest Crab chitters"),
    "entity.chest_crab.hurt": (chest_crab_hurt, 2, "Chest Crab squeaks"),
    "entity.chest_crab.death": (chest_crab_death, 1, "Chest Crab dies"),
    "entity.chest_crab.step": (chest_crab_step, 2, "Chest Crab scuttles"),
    "entity.chest_crab.hide": (chest_crab_hide, 1, "Chest Crab hides"),
    "entity.chest_crab.coin": (coin_jingle, 2, "Coins jingle"),
    "entity.lava_crab.ambient": (lava_crab_ambient, 2, "Lava Crab sizzles"),
    "entity.lava_crab.hurt": (lava_crab_hurt, 2, "Lava Crab hurts"),
    "entity.lava_crab.death": (lava_crab_death, 1, "Lava Crab cracks"),
    "entity.lava_crab.step": (lava_crab_step, 2, "Lava Crab scuttles"),
    "entity.castaway.ambient": (castaway_ambient, 3, "Castaway mutters"),
    "entity.castaway.hurt": (castaway_hurt, 2, "Castaway hurts"),
    "entity.castaway.death": (castaway_death, 1, "Castaway dies"),
    "entity.castaway.drink": (castaway_drink, 1, "Castaway drinks"),
    "entity.castaway.throw": (throw_whoosh, 2, "Castaway throws"),
    "entity.cursed_monkey.ambient": (monkey_ambient, 3, "Cursed Monkey hoots"),
    "entity.cursed_monkey.hurt": (monkey_hurt, 2, "Cursed Monkey screeches"),
    "entity.cursed_monkey.death": (monkey_death, 1, "Cursed Monkey dies"),
    "entity.cursed_monkey.step": (monkey_step, 2, "Cursed Monkey scampers"),
    "entity.map_merchant.ambient": (merchant_ambient, 3, "Map Merchant murmurs"),
    "entity.map_merchant.trade": (merchant_trade, 1, "Map Merchant trades"),
    "entity.map_merchant.hurt": (merchant_hurt, 2, "Map Merchant hurts"),
    "entity.map_merchant.death": (merchant_death, 1, "Map Merchant dies"),
    "entity.pirate_crew.ambient": (pirate_ambient, 3, "Pirate grumbles"),
    "entity.pirate_crew.hurt": (pirate_hurt, 2, "Pirate hurts"),
    "entity.pirate_crew.death": (pirate_death, 1, "Pirate dies"),
    "entity.ship_captain.ambient": (captain_ambient, 3, "Captain barks orders"),
    "entity.ship_captain.hurt": (captain_hurt, 2, "Captain hurts"),
    "entity.ship_captain.death": (captain_death, 1, "Captain dies"),
}


def loudness(x, target_rms=0.11):
    """Transient-heavy sounds (clacks, stings) peak-normalize to a whisper; saturate them up."""
    rms = np.sqrt(np.mean(x ** 2))
    if rms >= target_rms:
        return x
    drive = min(8.0, target_rms / max(rms, 1e-4))
    return normalize(np.tanh(x * drive) / np.tanh(drive))


# roster events (family-synthesized)
for _mob, (_fam, _pitch, _name) in ROSTER.items():
    for _kind, _n in KIND_VARIANTS.items():
        SOUNDS[f"entity.{_mob}.{_kind}"] = (
            (lambda rng, v, f=_fam, k=_kind, pt=_pitch: FAMILIES[f](k, rng, v, pt)), _n, f"{_name} {KIND_WORDS[_kind]}")


def write_ogg(x, path: Path):
    x = loudness(normalize(trim_tail(x)))
    fade = min(len(x), int(0.01 * SR))
    x[-fade:] *= np.linspace(1, 0, fade)
    pcm = (np.clip(x, -1, 1) * 32767).astype(np.int16)
    path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as td:
        wav = Path(td) / "s.wav"
        with wave.open(str(wav), "wb") as w:
            w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(wav), "-ac", "1", "-c:a", "libvorbis", "-q:a", "5", str(path)], check=True)
    return len(pcm) / SR


def main():
    sounds_json = ASSETS / "sounds.json"
    data = json.loads(sounds_json.read_text(encoding="utf-8-sig")) if sounds_json.exists() else {}
    lang = {}
    for event, (fn, variants, subtitle) in SOUNDS.items():
        mob, kind = event.split(".")[1], event.split(".")[2]
        files = []
        for v in range(variants):
            rng = np.random.default_rng(sum(map(ord, event)) * 31 + v)   # deterministic per event+variant
            name = f"{kind}{v + 1}" if variants > 1 else kind
            dur = write_ogg(fn(rng, v), OUT / mob / f"{name}.ogg")
            files.append(f"pixelpirates:mob/{mob}/{name}")
            print(f"  {event:34} {name:9} {dur:4.2f}s")
        entry = {"subtitle": f"subtitles.pixelpirates.{event}", "sounds": files}
        if kind == "song":
            entry["sounds"] = [{"name": f, "attenuation_distance": 32} for f in files]
        data[event] = entry
        lang[f"subtitles.pixelpirates.{event}"] = subtitle
    sounds_json.write_bytes((json.dumps(data, indent=2) + "\n").encode("utf-8"))
    lang_path = ASSETS / "lang/en_us.json"
    l = json.loads(lang_path.read_text(encoding="utf-8-sig"))
    l.update(lang)
    lang_path.write_bytes((json.dumps(l, indent=2, ensure_ascii=False) + "\n").encode("utf-8"))
    print(f"wrote {len(SOUNDS)} events")


if __name__ == "__main__":
    main()
