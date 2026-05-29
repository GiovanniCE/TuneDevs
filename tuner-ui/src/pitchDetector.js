// Browser audio pitch-detection helper based on Web Audio and the YIN algorithm; it provides detector object that emits pitch and input-level callbacks.

const DEFAULT_SAMPLE_RATE = 44100;
const DEFAULT_FFT_SIZE = 4096;
const YIN_THRESHOLD = 0.15;

// YIN estimates pitch by finding the repeating period in a time-domain buffer.
function yinDetect(buffer, sampleRate) {
  const halfLen = Math.floor(buffer.length / 2);
  const yinBuffer = new Float32Array(halfLen);

  // Difference function: lower values mean the waveform matches itself at tau.
  for (let tau = 0; tau < halfLen; tau++) {
    let sum = 0;
    for (let i = 0; i < halfLen; i++) {
      const delta = buffer[i] - buffer[i + tau];
      sum += delta * delta;
    }
    yinBuffer[tau] = sum;
  }

  yinBuffer[0] = 1;
  let runningSum = 0;
  // Cumulative mean normalization makes early false matches less attractive.
  for (let tau = 1; tau < halfLen; tau++) {
    runningSum += yinBuffer[tau];
    yinBuffer[tau] *= tau / runningSum;
  }

  let tau = 2;
  while (tau < halfLen) {
    if (yinBuffer[tau] < YIN_THRESHOLD) {
      // Slide to the local minimum, then use neighboring values for a smoother
      // fractional-period estimate instead of snapping to an integer sample.
      while (tau + 1 < halfLen && yinBuffer[tau + 1] < yinBuffer[tau]) {
        tau++;
      }

      const s0 = yinBuffer[tau - 1] ?? yinBuffer[tau];
      const s1 = yinBuffer[tau];
      const s2 = yinBuffer[tau + 1] ?? yinBuffer[tau];
      const betterTau = tau + (s0 - s2) / (2 * (s0 - 2 * s1 + s2) || 1);

      const frequency = sampleRate / betterTau;
      const confidence = 1 - yinBuffer[tau];

      // Ignore frequencies outside the useful guitar/bass range plus headroom.
      if (frequency < 30 || frequency > 1400) return null;
      return { frequency: Math.round(frequency * 100) / 100, confidence };
    }
    tau++;
  }

  return null;
}

export async function listAudioDevices() {
  const devices = await navigator.mediaDevices.enumerateDevices();
  const virtualIds = ["default", "communications"];
  // Hide browser-created aliases so the dropdown lists physical inputs only.
  return devices
    .filter((d) => d.kind === "audioinput" && !virtualIds.includes(d.deviceId))
    .map((d) => ({ deviceId: d.deviceId, label: d.label || `Microphone ${d.deviceId.slice(0, 8)}` }));
}

export async function requestAndListAudioDevices() {
  try {
    // Browsers usually reveal device labels only after the user grants mic
    // permission, so open and immediately close a short-lived stream first.
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    stream.getTracks().forEach((t) => t.stop());
  } catch {
    // ignore — permission denied before first device list
  }
  const devices = await listAudioDevices();
  const builtinKeywords = ["built-in", "internal", "realtek", "hdmi"];
  // External audio interfaces are more likely to be instrument inputs, so list
  // them before laptop microphones.
  return [
    ...devices.filter((d) => !builtinKeywords.some((kw) => d.label.toLowerCase().includes(kw))),
    ...devices.filter((d) => builtinKeywords.some((kw) => d.label.toLowerCase().includes(kw))),
  ];
}

export async function createPitchDetector(options = {}) {
  const { deviceId, fftSize = DEFAULT_FFT_SIZE, intervalMs = 80 } = options;

  const constraints = {
    audio: {
      // Disable browser processing so pitch detection sees the raw instrument
      // signal rather than an enhanced voice-call signal.
      echoCancellation: false,
      noiseSuppression: false,
      autoGainControl: false,
      ...(deviceId ? { deviceId: { exact: deviceId } } : {}),
    },
  };

  const stream = await navigator.mediaDevices.getUserMedia(constraints);
  const audioContext = new AudioContext({ sampleRate: DEFAULT_SAMPLE_RATE });
  const source = audioContext.createMediaStreamSource(stream);
  const analyser = audioContext.createAnalyser();
  analyser.fftSize = fftSize;

  source.connect(analyser);

  const buffer = new Float32Array(analyser.fftSize);
  let intervalId = null;
  let running = false;

  const detector = {
    onPitch: null,
    onLevel: null,

    start() {
      if (running) return;
      running = true;

      if (audioContext.state === "suspended") {
        audioContext.resume();
      }

      intervalId = setInterval(() => {
        analyser.getFloatTimeDomainData(buffer);

        let rms = 0;
        for (let i = 0; i < buffer.length; i++) rms += buffer[i] * buffer[i];
        rms = Math.sqrt(rms / buffer.length);

        if (detector.onLevel) {
          detector.onLevel(Math.min(rms, 1));
        }

        // Very quiet buffers are usually room noise and produce unstable pitch.
        if (rms < 0.005) return;

        const result = yinDetect(buffer, audioContext.sampleRate);
        if (result && detector.onPitch) {
          detector.onPitch(result);
        }
      }, intervalMs);
    },

    stop() {
      running = false;
      if (intervalId) {
        clearInterval(intervalId);
        intervalId = null;
      }

      for (const track of stream.getTracks()) {
        track.stop();
      }

      source.disconnect();
      audioContext.close();
    },

    get isRunning() {
      return running;
    },
  };

  return detector;
}
