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
| Chang Zhi Heng | S10241579H | Heng-Github1 |
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
| Current Weather Display | Chang Zhi Heng |
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
- Implemented map-based weather search using Google Maps Compose
- Enabled users to search locations by place name or postal code and reposition the map
- Added tap-to-select map interaction to retrieve latitude and longitude
- Integrated reverse geocoding to convert coordinates into readable place names
- Connected selected map locations to real-time weather retrieval using OpenWeather API
- Implemented saving selected locations as favourites to Firebase Firestore
- Ensured user-specific data storage by saving favourites under authenticated user IDs
- Handled error states for invalid locations, geocoding failures, and API errors
- Improved usability by combining visual map selection with weather data feedback

### Keagan Tan
- 

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
```
---

## Screenshots Of User Interface

<img width="381" height="766" alt="Screenshot 2026-02-08 232306" src="https://github.com/user-attachments/assets/b0d95eab-a718-48a3-9500-5a3451ecc61f" />
<img width="396" height="788" alt="signup png" src="https://github.com/user-attachments/assets/8782a65f-2288-4e76-a5d7-561bf1793582" />
<img width="783" height="775" alt="friends_search png" src="https://github.com/user-attachments/assets/081a856d-52d5-4303-b0b3-ec9da9d5ea71" />
<img width="782" height="779" alt="Screenshot 2026-02-08 232152" src="https://github.com/user-attachments/assets/9fb738ce-1e23-4ffa-8e16-6148a1e3fd12" />
<img width="393" height="780" alt="Screenshot 2026-02-08 232203" src="https://github.com/user-attachments/assets/5ed90dfa-cbe0-463d-bb06-2dd60b1859da" />
<img width="388" height="770" alt="Screenshot 2026-02-08 232214" src="https://github.com/user-attachments/assets/cf1825e2-c10b-4e25-8d50-ef785c398081" />
<img width="397" height="778" alt="Screenshot 2026-02-08 232226" src="https://github.com/user-attachments/assets/b9066e33-6b17-4357-bb17-f5b2f872c472" />
<img width="391" height="774" alt="Screenshot 2026-02-08 232237" src="https://github.com/user-attachments/assets/5ae4b8bc-2268-4045-9ad4-372a4f8a8df8" />
<img width="403" height="793" alt="Screenshot 2026-02-08 232247" src="https://github.com/user-attachments/assets/0f65e880-8793-4a9a-bba0-33c6fa9abbaa" />


