# Specification Document

Please fill out this document to reflect your team's project. This is a living document and will need to be updated regularly. You may also remove any section to its own document (e.g. a separate standards and conventions document), however you must keep the header and provide a link to that other document under the header.

Also, be sure to check out the Wiki for information on how to maintain your team's requirements.

## TeamName

TuneDevs

### Project Abstract

<!--A one paragraph summary of what the software will do.-->

The primary objective of our project is to create a small form factor device that allows guitarists to tune their instruments in a studio setting, all the way to noisy bars. This handheld device will use real time information to auto-tune the instrument via a contact microphone, so that the tuner works in many environments.

Please view this file's source to see `<!--comments-->` with guidance on how you might use the different sections of this document. 

### Customer

Our customers will be musicians that appreciate the convenience of being able to make these tunings on the fly. Many established musicians "tune by ear", but the addition of multiple profiles to be able to instantaneously tune depending on the song will make the musician more efficient, and be able to focus more on the art they are presenting.

### Specification

<!--A detailed specification of the system. UML, or other diagrams, such as finite automata, or other appropriate specification formalisms, are encouraged over natural language.-->

<!--Include sections, for example, illustrating the database architecture (with, for example, an ERD).-->

<!--Included below are some sample diagrams, including some example tech stack diagrams.-->

#### Technology Stack

```mermaid
flowchart RL
subgraph Front End
    A("JavaScript: React 19 + Vite\n(Web Audio API, STOMP.js)")
end

subgraph Back End
    B("Java 21: Spring Boot 3.3\n(REST + WebSocket/STOMP)")
end

subgraph Database
    C[(MySQL 8)]
end

A <-->|"REST API + WebSocket (STOMP/SockJS)"| B
B <-->|"JDBC (JdbcTemplate)"| C
```


#### Database

```mermaid
---
title: Database ERD for Smart Guitar Tuner
---
erDiagram
    users ||--o{ user_tunings : "owns"
    users ||--|| user_preferences : "has"
    user_preferences ||--o{ favorite_songs : "contains"
    tunings ||--o{ tuning_strings : "has"
    user_tunings ||--o{ tuning_strings : "has"

    users {
        int id PK
        string username
        string password_hash
    }

    tunings {
        int id PK
        string tuning_key
        string name
        string instrument
    }

    tuning_strings {
        int id PK
        int tuning_id FK
        int string_number
        string label
        string note
        float freq
    }

    user_tunings {
        int id PK
        int user_id FK
        string tuning_key
        string name
        string instrument
    }

    user_preferences {
        int id PK
        int user_id FK
        string preferred_tuning_keys
    }

    favorite_songs {
        int id PK
        int preference_id FK
        string song_name
        string artist
        string tuning_key
    }
```

#### Class Diagram

```mermaid
---
title: Backend Service Layer (Current)
---
classDiagram
    class AuthController {
        + login(AuthRequest) ResponseEntity
        + register(AuthRequest) ResponseEntity
    }

    class TuningCatalogController {
        + getStandardTunings() List
        + getUserTunings(username) List
        + createCustomTuning(username, request) TuningDefinitionDto
        + deleteCustomTuning(username, key) ResponseEntity
    }

    class PreferencesController {
        + getPreferences(username) UserPreferences
        + savePreferences(username, prefs) ResponseEntity
    }

    class AudioInputController {
        + handleFrequency(freq, stringId) void
    }

    class UserPreferencesService {
        + validateLogin(username, password) boolean
        + register(username, password) void
        + getPreferences(username) UserPreferences
        + savePreferences(username, prefs) void
    }

    class TuningCatalogService {
        + getStandardTunings() List
        + getTuningsForUser(username) List
        + createCustomTuning(username, request) TuningDefinitionDto
        + deleteCustomTuning(username, key) void
    }

    class TunerService {
        - tunerMode : String
        + onStartup() void
        + onShutdown() void
    }

    class TuningUpdateMapper {
        + fromPythonJson(json) TuningUpdate
        + mockUpdate(instrument) TuningUpdate
    }

    AuthController --> UserPreferencesService
    TuningCatalogController --> TuningCatalogService
    PreferencesController --> UserPreferencesService
    AudioInputController --> TuningUpdateMapper
    TunerService --> TuningUpdateMapper
```

#### Flowchart

```mermaid
---
title: Smart Tuner Logic Flow
---
graph TD;
    Start([Start System]) --> Init_Hardware[Initialize Mic & Motors];
    Init_Hardware --> Select_Mode{Select Mode};
    
    %% Standard/Auto Tuning Path
    Select_Mode -->|Auto-Tune| Listen_Pitch[/Input: Audio Stream/];
    Listen_Pitch --> FFT_Process[Fast Fourier Transform];
    FFT_Process --> Pitch_Detect{Pitch Detected?};
    Pitch_Detect -- No --> Listen_Pitch;
    Pitch_Detect -- Yes --> Compare_Target[Compare vs Target Freq];
    
    Compare_Target --> Calculate_PID[Calculate PID Error];
    Calculate_PID --> In_Tune{In Threshold?};
    In_Tune -- No --> Drive_Motor[Drive Servo Motor];
    Drive_Motor --> Listen_Pitch;
    In_Tune -- Yes --> Display_Green[/Display: In Tune/];
```

#### Behavior

```mermaid
---
title: Device State Machine
---
stateDiagram-v2
    [*] --> Standby
    
    Standby --> Listening : Audio Detected (> -40dB)
    Listening --> Standby : Silence / Timeout
    
    state Listening {
        [*] --> AnalyzingFreq
        AnalyzingFreq --> MotorActive : Frequency Stable & Off-Pitch
        MotorActive --> AnalyzingFreq : Motor Step Complete
        AnalyzingFreq --> LockedIn : Frequency Matches Target
    }
    
    MotorActive --> SafetyStop : Current Spike (String Jammed)
    SafetyStop --> ErrorState : Display "Check Peg"
    ErrorState --> Standby : Manual Reset
    
    LockedIn --> LessonMode : User requests verification
    LessonMode --> Standby : Lesson Complete
```


### Standards & Conventions

<!--This is a link to a seperate coding conventions document / style guide-->
[Style Guide & Conventions](STYLE.md)
