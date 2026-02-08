# 🌤️ Weather Buddies

## Disclaimer
This is a **student assignment project** for the **Kotlin App Development** module at **Ngee Ann Polytechnic**.  
Developed strictly for **educational purposes only**.

---

## Team Members
| Name | Student ID | GitHub Username |
|------|------------|----------------|
| Phua Yong Xiang | S10258483F | Pyongxiang |
| Evan Goh | S10258381G | evangohh |
| Zhi Heng | _(add)_ | Heng-Github1 |
| Keagan Tan | _(add)_ | GITNumberx |

---

## Application Overview
**Weather Buddies** is a mobile weather application that allows users to check **real-time weather conditions**, **location-based forecasts**, and **weather alerts**.  
In addition to weather information, the app supports **social interaction features**, enabling users to communicate and share weather experiences with friends.

---

## Current State of Deployment
- Platform: **Android (Kotlin + Jetpack Compose)**
- Status: **Functional prototype**
- Deployment: **Local build / Emulator**
- APIs & Services:
  - OpenWeather API
  - Firebase Authentication
  - Firebase Firestore

---

## Motivation & Objectives
The objective of Weather Buddies is to make weather checking more **engaging, practical, and social**.  
Instead of relying solely on generic forecasts, users can view **location-specific weather**, receive **background alerts**, and share real-time conditions within their social groups.

---

## Use of Generative AI Tools
- **ChatGPT** and **Gemini LLMs** were used as development assistance tools
- Used for:
  - Debugging Kotlin and Jetpack Compose issues
  - Understanding Android APIs and workflows
  - Improving code structure and documentation
- All generated outputs were reviewed and adapted by the team

---

## Stage 1 – Features & Task Allocation
| Feature | Team Member |
|--------|------------|
| Login Page | Phua Yong Xiang |
| Current Weather Display | Zhi Heng |
| “Use My Location” Function | Evan Goh |
| Forecast Screen | Keagan Tan |

Stage 1 focused on implementing **core functionality**, navigation, and API integration.

---

## Stage 2 – Enhanced & New Functionalities
- Cloud-synced favourite locations using Firestore
- Background weather alerts using WorkManager
- Improved location search with fallback handling
- Weather detail widgets
- Notification permission handling (Android 13+)
- Social features added for communication
- Text-to-Speech weather narration
- UI and usability improvements

---

## Individual Contributions (Stage 2)

### Evan Goh
- Location-based weather retrieval
- Location search and geocoding logic
- Forecast page integration
- Weather detail widgets
- Background notification alerts
- Notification permission handling
- Designed app icon

### Phua Yong Xiang
- Authentication flow improvements
- Stored preferences for “Remember Me”
- User account management
- Friends feature
- Friend search
- View favourite locations from friends
- Chat room for 1-to-1 friends
- Chat room for multiple users
- UI and navigation refinements
- Designed app icon

### Zhi Heng
- Current weather data handling
- Weather UI presentation
- API integration support

### Keagan Tan
- Forecast feature implementation
- Forecast screen UI
- Navigation and testing support

---

## New Concepts Applied
- **Cloud NoSQL Database (Firestore)** – user-specific data persistence
- **Background Processing (WorkManager)** – reliable weather alerts
- **Modern Notification Handling** – channels and runtime permissions
- **Jetpack Compose** – reactive, state-driven UI
- **Text-to-Speech** – accessibility enhancement

---

## System Architecture Diagram

```mermaid
flowchart LR
  User --> UI["Jetpack Compose UI"]
  UI --> VM["State / ViewModel"]
  VM --> Repo["WeatherRepository"]

  Repo --> OW["OpenWeather API"]
  Repo --> Geo["Geocoding Services"]
  Repo --> Rev["Reverse Geocoding"]

  UI --> Auth["Firebase Authentication"]
  Auth --> FS["Firestore (User Data)"]

  VM --> Local["Local Storage"]
  VM --> WM["WorkManager"]
  WM --> Notif["Notification System"]

  UI --> TTS["Text-to-Speech"]
