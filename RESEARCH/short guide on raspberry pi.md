## Raspberry Pi Quick Guide

### What is it?
**Raspberry Pi** is a small, low-cost computer that runs Linux and is widely used for programming, IoT, and embedded systems.

---

### Basic Setup
1. Install **Raspberry Pi OS** using Raspberry Pi Imager  
2. Insert the SD card and power on the device  
3. Update system:

sudo apt update && sudo apt upgrade


---

### Simple GPIO Example (Blink LED)

import RPi.GPIO as GPIO
import time

GPIO.setmode(GPIO.BCM)
GPIO.setup(18, GPIO.OUT)

while True:
GPIO.output(18, 1)
time.sleep(1)
GPIO.output(18, 0)
time.sleep(1)


---

### Common Uses
- IoT (smart home, sensors)
- Servers (web server, NAS)
- Embedded systems (robotics, automation)
- AI/ML (lightweight models)

---

### Remote Access

ssh pi@<ip_address>


---

### Pros / Cons
**Pros**: cheap, low power, flexible  
**Cons**: limited performance, slower storage  

---

### Summary
Raspberry Pi is a great platform for learning systems programming, hardware interaction, and building real-world projects.