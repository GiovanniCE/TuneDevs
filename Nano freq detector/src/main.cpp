// Arduino Nano firmware for detecting or reporting frequency-counter signals; it provides serial or hardware signal data for tuner frequency measurement.

#include <Arduino.h>
#include <arduinoFFT.h>
#include <math.h>

constexpr uint8_t ANALOG_PIN = A0;
constexpr uint16_t FFT_SAMPLE_COUNT = 512;
constexpr float FFT_SAMPLING_FREQUENCY_HZ = 2000.0f;
constexpr uint8_t PEAKS_TO_PRINT = 5;

constexpr float TUNER_SAMPLE_RATE_HZ = 4000.0f;
constexpr uint32_t TUNER_GATE_TIME_US = 2500000UL; // 2.5 s (keep under 3 s)
constexpr float TUNER_MIN_FREQ_HZ = 70.0f;
constexpr float TUNER_MAX_FREQ_HZ = 360.0f;
constexpr float HYSTERESIS_MIN_ADC = 6.0f;
constexpr float HYSTERESIS_ENVELOPE_RATIO = 0.22f;
constexpr float PERIOD_TOLERANCE_RATIO = 0.25f;

static_assert((FFT_SAMPLE_COUNT & (FFT_SAMPLE_COUNT - 1)) == 0, "FFT_SAMPLE_COUNT must be a power of two");

float realBuffer[FFT_SAMPLE_COUNT];
float imagBuffer[FFT_SAMPLE_COUNT];
ArduinoFFT<float> fft(realBuffer, imagBuffer, FFT_SAMPLE_COUNT, FFT_SAMPLING_FREQUENCY_HZ);

float dcEstimate = 512.0f;

struct Peak {
  uint16_t bin;
  float magnitude;
  float frequencyHz;
};

float computeMean(const float* values, uint16_t count) {
  float sum = 0.0f;
  for (uint16_t index = 0; index < count; index++) {
    sum += values[index];
  }
  return sum / static_cast<float>(count);
}

void insertPeak(Peak* topPeaks, uint16_t bin, float magnitude, float frequencyHz) {
  if (magnitude <= topPeaks[PEAKS_TO_PRINT - 1].magnitude) {
    return;
  }

  topPeaks[PEAKS_TO_PRINT - 1].bin = bin;
  topPeaks[PEAKS_TO_PRINT - 1].magnitude = magnitude;
  topPeaks[PEAKS_TO_PRINT - 1].frequencyHz = frequencyHz;

  for (int8_t position = PEAKS_TO_PRINT - 1; position > 0; position--) {
    if (topPeaks[position].magnitude > topPeaks[position - 1].magnitude) {
      Peak temp = topPeaks[position - 1];
      topPeaks[position - 1] = topPeaks[position];
      topPeaks[position] = temp;
    }
  }
}

float readCenteredSample() {
  float raw = static_cast<float>(analogRead(ANALOG_PIN));
  dcEstimate += (raw - dcEstimate) * 0.002f;
  return raw - dcEstimate;
}

float measureFundamentalHz() {
  const uint32_t samplePeriodUs = static_cast<uint32_t>(1000000.0f / TUNER_SAMPLE_RATE_HZ);
  const float minPeriodUs = 1000000.0f / TUNER_MAX_FREQ_HZ;
  const float maxPeriodUs = 1000000.0f / TUNER_MIN_FREQ_HZ;

  uint32_t gateStartUs = micros();
  uint32_t nextSampleUs = gateStartUs;

  float previousSample = 0.0f;
  bool hasPrevious = false;
  bool armedBelow = false;

  float envelope = 20.0f;
  float referencePeriodUs = 0.0f;
  float periodSumUs = 0.0f;
  uint16_t acceptedPeriods = 0;
  float lastCrossingUs = 0.0f;
  bool hasLastCrossing = false;

  while ((micros() - gateStartUs) < TUNER_GATE_TIME_US) {
    while ((int32_t)(micros() - nextSampleUs) < 0) {
      // wait for next sample moment
    }

    float sample = readCenteredSample();
    nextSampleUs += samplePeriodUs;

    float absSample = fabsf(sample);
    envelope += (absSample - envelope) * 0.01f;
    float hysteresis = envelope * HYSTERESIS_ENVELOPE_RATIO;
    if (hysteresis < HYSTERESIS_MIN_ADC) {
      hysteresis = HYSTERESIS_MIN_ADC;
    }

    if (sample <= -hysteresis) {
      armedBelow = true;
    }

    if (armedBelow && hasPrevious && previousSample < hysteresis && sample >= hysteresis) {
      float y1 = previousSample - hysteresis;
      float y2 = sample - hysteresis;
      float fraction = 0.0f;
      float denominator = y2 - y1;
      if (fabsf(denominator) > 0.0001f) {
        fraction = (-y1) / denominator;
      }
      if (fraction < 0.0f) {
        fraction = 0.0f;
      } else if (fraction > 1.0f) {
        fraction = 1.0f;
      }

      float crossingUs = static_cast<float>(nextSampleUs - samplePeriodUs) + (fraction * static_cast<float>(samplePeriodUs));

      if (hasLastCrossing) {
        float periodUs = crossingUs - lastCrossingUs;
        bool inRange = periodUs >= minPeriodUs && periodUs <= maxPeriodUs;

        if (inRange) {
          bool acceptPeriod = false;
          if (referencePeriodUs <= 0.0f) {
            acceptPeriod = true;
            referencePeriodUs = periodUs;
          } else {
            float lower = referencePeriodUs * (1.0f - PERIOD_TOLERANCE_RATIO);
            float upper = referencePeriodUs * (1.0f + PERIOD_TOLERANCE_RATIO);
            if (periodUs >= lower && periodUs <= upper) {
              acceptPeriod = true;
              referencePeriodUs = (referencePeriodUs * 0.85f) + (periodUs * 0.15f);
            }
          }

          if (acceptPeriod) {
            periodSumUs += periodUs;
            acceptedPeriods++;
          }
        }
      }

      lastCrossingUs = crossingUs;
      hasLastCrossing = true;
      armedBelow = false;
    }

    previousSample = sample;
    hasPrevious = true;
  }

  if (acceptedPeriods < 8 || periodSumUs <= 0.0f) {
    return NAN;
  }

  float averagePeriodUs = periodSumUs / static_cast<float>(acceptedPeriods);
  return 1000000.0f / averagePeriodUs;
}

void acquireFftSamples() {
  const uint32_t samplePeriodUs = static_cast<uint32_t>(1000000.0f / FFT_SAMPLING_FREQUENCY_HZ);
  uint32_t nextSampleUs = micros();

  for (uint16_t index = 0; index < FFT_SAMPLE_COUNT; index++) {
    while ((int32_t)(micros() - nextSampleUs) < 0) {
      // wait for precise sample moment
    }
    nextSampleUs += samplePeriodUs;

    realBuffer[index] = static_cast<float>(analogRead(ANALOG_PIN));
    imagBuffer[index] = 0.0f;
  }
}

void removeDcOffset() {
  float mean = computeMean(realBuffer, FFT_SAMPLE_COUNT);
  for (uint16_t index = 0; index < FFT_SAMPLE_COUNT; index++) {
    realBuffer[index] -= mean;
  }
}

float interpolatePeakFrequency(uint16_t bin) {
  if (bin == 0 || bin >= (FFT_SAMPLE_COUNT / 2) - 1) {
    return (static_cast<float>(bin) * FFT_SAMPLING_FREQUENCY_HZ) / static_cast<float>(FFT_SAMPLE_COUNT);
  }

  float left = realBuffer[bin - 1];
  float center = realBuffer[bin];
  float right = realBuffer[bin + 1];
  float denominator = left - (2.0f * center) + right;
  float delta = 0.0f;

  if (fabsf(denominator) > 0.0001f) {
    delta = 0.5f * (left - right) / denominator;
    if (delta > 0.5f) {
      delta = 0.5f;
    } else if (delta < -0.5f) {
      delta = -0.5f;
    }
  }

  float refinedBin = static_cast<float>(bin) + delta;
  return (refinedBin * FFT_SAMPLING_FREQUENCY_HZ) / static_cast<float>(FFT_SAMPLE_COUNT);
}

void printPeakList() {
  Peak topPeaks[PEAKS_TO_PRINT];
  for (uint8_t i = 0; i < PEAKS_TO_PRINT; i++) {
    topPeaks[i] = {0, 0.0f, 0.0f};
  }

  float maxMagnitude = 0.0f;
  const uint16_t maxBin = (FFT_SAMPLE_COUNT / 2) - 1;
  for (uint16_t bin = 1; bin <= maxBin; bin++) {
    if (realBuffer[bin] > maxMagnitude) {
      maxMagnitude = realBuffer[bin];
    }
  }

  float threshold = maxMagnitude * 0.12f;
  for (uint16_t bin = 2; bin < maxBin; bin++) {
    float mag = realBuffer[bin];
    bool isLocalMaximum = (mag > realBuffer[bin - 1]) && (mag >= realBuffer[bin + 1]);
    float freq = (static_cast<float>(bin) * FFT_SAMPLING_FREQUENCY_HZ) / static_cast<float>(FFT_SAMPLE_COUNT);
    bool inUsefulRange = (freq >= 60.0f && freq <= 1500.0f);

    if (isLocalMaximum && mag >= threshold && inUsefulRange) {
      insertPeak(topPeaks, bin, mag, interpolatePeakFrequency(bin));
    }
  }

  Serial.println(F("Peaks (Hz | magnitude):"));
  bool printedAny = false;
  for (uint8_t index = 0; index < PEAKS_TO_PRINT; index++) {
    if (topPeaks[index].magnitude <= 0.0) {
      continue;
    }

    Serial.print(F("  "));
    Serial.print(topPeaks[index].frequencyHz, 2);
    Serial.print(F(" Hz | "));
    Serial.println(topPeaks[index].magnitude, 2);
    printedAny = true;
  }

  if (!printedAny) {
    Serial.println(F("  No strong peaks found"));
  }
}

void setup() {
  Serial.begin(115200);
  pinMode(ANALOG_PIN, INPUT);
  analogReference(DEFAULT);

  Serial.println(F("Guitar tuner + FFT analyzer ready."));
  Serial.println(F("Input: A0 (bias sine to mid-supply, 0-5V range)."));
  Serial.println(F("Tuner gate: 2.5 s (high-accuracy period averaging)."));
  Serial.println(F("FFT gate: 512 samples @ 2000 Hz (~0.256 s)."));
}

void loop() {
  uint32_t tunerStartMs = millis();
  float tunedHz = measureFundamentalHz();
  uint32_t tunerElapsedMs = millis() - tunerStartMs;

  uint32_t fftStartMs = millis();
  acquireFftSamples();
  uint32_t fftElapsedMs = millis() - fftStartMs;

  removeDcOffset();

  fft.windowing(FFTWindow::Hamming, FFTDirection::Forward);
  fft.compute(FFTDirection::Forward);
  fft.complexToMagnitude();

  float dominantHz = fft.majorPeak();

  Serial.println(F("--------------------------------"));
  Serial.print(F("Tuner gate: "));
  Serial.print(tunerElapsedMs);
  Serial.println(F(" ms"));
  if (isnan(tunedHz)) {
    Serial.println(F("Tuned fundamental: no stable lock"));
  } else {
    Serial.print(F("Tuned fundamental: "));
    Serial.print(tunedHz, 3);
    Serial.println(F(" Hz"));
  }

  Serial.print(F("FFT gate: "));
  Serial.print(fftElapsedMs);
  Serial.println(F(" ms"));
  Serial.print(F("Dominant peak: "));
  Serial.print(dominantHz, 2);
  Serial.println(F(" Hz"));
  printPeakList();

  delay(120);
}