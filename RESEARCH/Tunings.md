# Research Report  
## Guitar Tunings, Frequencies, and Genre Associations

### Summary of Work
My research focused on identifying common guitar tunings, their associated string frequencies, and how different musical genres use alternative tunings. I reviewed standard pitch references (A4 = 440 Hz), compiled frequency tables for standard and alternate tunings, and analyzed how tunings impact playable notes on each string. I also examined which genres most commonly use specific tunings to determine how our autotuner could support preset tuning modes.

### Motivation
This research is important for our guitar autotuner project because accurate tuning depends on precise frequency-to-note mapping. Supporting multiple tunings improves usability and allows musicians from different genres to use the system effectively. Additionally, implementing alternate tunings requires correct base frequencies and an understanding of how semitone shifts affect pitch detection.

Since our project may later include selectable tuning modes or genre-based presets, documenting the most common tunings and their frequency mappings strengthens both backend detection logic and front-end feature planning.

### Time Spent
- 30 minutes researching standard pitch frequencies and alternate tunings  
- 20 minutes compiling and verifying frequency tables  
- 15 minutes organizing tunings by genre and documenting findings  

**Total Time: ~1 hour 5 minutes**

---

## Standard Tuning (EADGBE)

Standard tuning (from lowest string to highest):

| String | Note | Frequency (Hz) |
|--------|------|---------------|
| 6th    | E2   | 82.41 |
| 5th    | A2   | 110.00 |
| 4th    | D3   | 146.83 |
| 3rd    | G3   | 196.00 |
| 2nd    | B3   | 246.94 |
| 1st    | E4   | 329.63 |

---

## Common Alternate Tunings

### Drop D (DADGBE)

| String | Note | Frequency (Hz) |
|--------|------|---------------|
| 6th | D2 | 73.42 |
| 5th | A2 | 110.00 |
| 4th | D3 | 146.83 |
| 3rd | G3 | 196.00 |
| 2nd | B3 | 246.94 |
| 1st | E4 | 329.63 |

---

### D Standard (DGCFAD)

| String | Note | Frequency (Hz) |
|--------|------|---------------|
| 6th | D2 | 73.42 |
| 5th | G2 | 98.00 |
| 4th | C3 | 130.81 |
| 3rd | F3 | 174.61 |
| 2nd | A3 | 220.00 |
| 1st | D4 | 293.66 |

---

### Open G (DGDGBD)

| String | Note | Frequency (Hz) |
|--------|------|---------------|
| 6th | D2 | 73.42 |
| 5th | G2 | 98.00 |
| 4th | D3 | 146.83 |
| 3rd | G3 | 196.00 |
| 2nd | B3 | 246.94 |
| 1st | D4 | 293.66 |

---

### Open D (DADF#AD)

| String | Note | Frequency (Hz) |
|--------|------|---------------|
| 6th | D2 | 73.42 |
| 5th | A2 | 110.00 |
| 4th | D3 | 146.83 |
| 3rd | F#3 | 185.00 |
| 2nd | A3 | 220.00 |
| 1st | D4 | 293.66 |

---

## Frequency Calculation Formula

To calculate frequencies across semitones:

f = f₀ × 2^(n/12)

Where:
- f₀ = base frequency  
- n = number of semitone steps  

This formula allows backend logic to dynamically calculate note frequencies rather than hardcoding every possible value.

---

## Notes on the Low E String (Standard Tuning Example)

| Fret | Note | Frequency (Hz) |
|------|------|---------------|
| 0 | E2 | 82.41 |
| 1 | F2 | 87.31 |
| 2 | F#2 | 92.50 |
| 3 | G2 | 98.00 |
| 5 | A2 | 110.00 |
| 7 | B2 | 123.47 |
| 12 | E3 | 164.81 |

---

## Genres and Common Tunings

### Rock
- Standard (EADGBE)
- Drop D

### Metal
- Drop D
- D Standard
- Drop C
- C Standard

### Blues
- Standard
- Open G
- Open D

### Folk
- Standard
- DADGAD
- Open D

### Country
- Standard
- Open G

### Alternative / Grunge
- Drop D
- Eb Standard

---

## Results
Through this research, I developed a structured frequency reference for multiple tunings and genres. This supports implementation of:

- Multi-tuning selection in the UI  
- Accurate pitch detection logic  
- Genre-based preset tuning options  
- Scalable frequency-to-note mapping  

These findings directly support backend frequency validation and future feature expansion of the autotuner system.

---

## Sources

- Yamaha Guitar Tuning Guide [^1]  
- Fender Standard Guitar Tuning Reference [^2]  
- Engineering Toolbox – Musical Note Frequencies [^3]  
- JustinGuitar – Alternate Tunings Guide [^4]  
- OpenAI ChatGPT (AI-assisted compilation and formatting) [^5]

[^1]: https://hub.yamaha.com/guitars/guitar-basics/how-to-tune-a-guitar/
[^2]: https://www.fender.com/articles/how-to/how-to-tune-a-guitar
[^3]: https://www.engineeringtoolbox.com/musical-notes-frequencies-d_1610.html
[^4]: https://www.justinguitar.com/guitar-lessons/alternate-tunings
[^5]: https://openai.com/chatgpt

---

## AI Assistance Disclosure

Portions of this research, including frequency table compilation and source identification, were assisted by OpenAI’s ChatGPT. All technical information and references were reviewed and verified against external published tuning and frequency resources before inclusion in this report.