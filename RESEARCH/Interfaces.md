# Research Report
## Connecting an Electric Guitar to a Raspberry Pi for Frequency Detection

### Summary of Work
This research explored methods for connecting an electric guitar directly to a Raspberry Pi in order to capture a clean audio signal for frequency detection. The main goal was to avoid using a microphone because environmental noise and room acoustics can negatively impact pitch detection accuracy.

Three possible approaches were investigated:

1. Using a **microphone connected to the Raspberry Pi**
2. Using an **analog-to-digital converter (ADC) with a guitar input circuit**
3. Using a **USB audio interface with a 1/4-inch instrument jack**

After comparing complexity, signal quality, and ease of implementation, the research determined that using a **USB audio interface with a 1/4-inch instrument input** is the most practical and reliable solution.

---

## Motivation

Our guitar autotuner project requires accurate frequency detection of guitar strings. Using a microphone introduces several problems:

- Background noise from the environment  
- Reflections and room acoustics  
- Difficulty isolating the guitar signal  
- Reduced accuracy for pitch detection algorithms  

Because electric guitars already output an electrical signal through a **1/4″ instrument cable**, directly capturing that signal eliminates most noise sources.

However, the Raspberry Pi **does not include a built-in analog audio input**, meaning the guitar signal cannot be connected directly. A device capable of converting the analog signal into digital audio data is required.

This research investigates viable hardware methods to achieve that while ensuring the captured signal is clean enough for accurate pitch detection.

---

## Option 1: Microphone Input

### Description
The simplest approach would be to use a USB microphone or a microphone module connected to the Raspberry Pi.

### Advantages
- Very easy to implement  
- Minimal hardware setup  
- Widely documented  

### Disadvantages
- Sensitive to background noise  
- Picks up other instruments or voices  
- Less reliable pitch detection  
- Requires careful microphone placement  

Because our project focuses on **accurate frequency detection**, these limitations make microphones a less ideal solution.

---

## Option 2: ADC Circuit with Guitar Input

### Description
Another option is to connect the guitar to an **analog front-end circuit** and then feed the signal into an **ADC module** (such as MCP3008).

Typical signal chain:

Guitar  
↓  
1/4" Jack  
↓  
Preamp / buffer circuit  
↓  
ADC (MCP3008)  
↓  
SPI  
↓  
Raspberry Pi  

### Challenges

Electric guitars produce a **high-impedance signal (~1 MΩ)** and a relatively small voltage signal. Directly connecting the guitar to an ADC without conditioning can cause:

- Signal attenuation  
- Poor frequency response  
- Noise and distortion  

A proper implementation would require:

- High impedance buffer  
- Amplifier stage  
- Filtering  
- ADC configuration  

### Advantages

- Fully custom hardware  
- Educational for embedded signal processing  

### Disadvantages

- More complex analog design  
- Requires additional circuit design  
- Lower audio sampling performance compared to dedicated audio hardware  

Because our project timeline focuses on software and signal processing, this approach may introduce unnecessary hardware complexity.

---

## Option 3 (Recommended): USB Audio Interface

### Description

A **USB audio interface** converts analog audio signals into digital audio streams that the Raspberry Pi can read via USB.

Signal path:

Guitar  
↓  
1/4" Instrument Cable  
↓  
USB Audio Interface  
↓  
USB  
↓  
Raspberry Pi  

The interface performs:

- High impedance input buffering  
- Analog-to-digital conversion  
- Audio sampling  
- USB audio streaming  

This approach is widely used in music production and guitar recording.

### Advantages

- Clean direct signal  
- Built-in high impedance instrument input  
- High quality analog-to-digital conversion  
- Plug-and-play with Linux audio drivers  
- Minimal hardware design required  

### Disadvantages

- Requires an external device  

However, the reliability and signal quality benefits strongly outweigh these disadvantages.

---

## Available Hardware

During this research it was confirmed that a **USB audio interface is already available for use in the project**. The interface includes a **1/4" instrument input and USB output**, which allows it to be connected directly to the Raspberry Pi without requiring additional analog circuitry.

Because this hardware is already available, the USB audio interface approach is especially practical for the project since it avoids additional hardware purchases while still providing a clean and reliable guitar signal.

---

## Implementation with Raspberry Pi

Once connected, the Raspberry Pi can access the audio device using standard Linux audio systems such as:

- ALSA  
- PulseAudio  
- PyAudio  
- SoundDevice (Python)

Example audio capture pipeline:

Guitar → Audio Interface → Raspberry Pi → Python Audio Stream → FFT → Frequency Detection

Typical processing steps:

1. Capture audio samples from the interface  
2. Perform a Fast Fourier Transform (FFT)  
3. Identify the dominant frequency  
4. Compare detected frequency with target tuning frequencies  

This architecture integrates cleanly with the software portion of the autotuner project.

---

## Expected Frequency Range of Guitar Strings

For tuning purposes, the system only needs to detect frequencies within the range of standard guitar strings.

| String | Note | Frequency |
|------|------|------|
| Low E | E2 | 82.41 Hz |
| A | A2 | 110.00 Hz |
| D | D3 | 146.83 Hz |
| G | G3 | 196.00 Hz |
| B | B3 | 246.94 Hz |
| High E | E4 | 329.63 Hz |

This means the tuner primarily needs to detect frequencies in approximately the range:

**80 Hz – 330 Hz**

Restricting the detection range can improve FFT performance and reduce false detections from noise or harmonics.

---

## Results

The research concluded that using a **USB audio interface with a 1/4-inch instrument jack is the most viable solution** for connecting a guitar to the Raspberry Pi.

This method provides:

- Cleaner signal input  
- Reduced environmental noise  
- Accurate frequency detection  
- Minimal hardware development time  

Capturing the signal directly from the guitar instead of through a microphone will allow the frequency detection algorithm to operate on a cleaner waveform, improving pitch detection accuracy.

---

## Time Spent

- 25 minutes researching Raspberry Pi audio input limitations  
- 20 minutes investigating guitar signal characteristics and input requirements  
- 15 minutes comparing USB audio interface solutions  

**Total Time: ~1 hour**

---

## Sources

- Raspberry Pi Documentation – Audio Configuration [^1]  
- Adafruit – MCP3008 ADC Guide [^2]  
- SparkFun – Audio Signal Basics [^3]  
- ALSA Project – USB Audio Devices [^4]  
- OpenAI ChatGPT (AI-assisted research organization) [^5]

[^1]: https://www.raspberrypi.com/documentation/computers/audio.html  
[^2]: https://learn.adafruit.com/mcp3008-spi-adc  
[^3]: https://learn.sparkfun.com/tutorials/audio-basics  
[^4]: https://www.alsa-project.org/wiki/USB-Audio  
[^5]: https://openai.com/chatgpt

---

## AI Assistance Disclosure

Portions of this report were prepared with assistance from OpenAI’s ChatGPT for organizing research and locating relevant resources. All technical information and references were reviewed and verified before inclusion.