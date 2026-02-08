# 🌤️ Weather Buddies

## Disclaimer
This is a **student assignment project** for the **Kotlin App Development** module at **Ngee Ann Polytechnic**.  
Developed strictly for **educational purposes only**.

---

## Team Members
| Name | Student ID | GitHub Username |
|------|------------|----------------|
| Phua Yong Xiang | _(add)_ | Pyongxiang |
| Evan Goh | _(add)_ | evangohh |
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

### Phua Yong Xiang
- Authentication flow improvements
- User account management
- UI and navigation refinements

### Zhi Heng
- Weather data presentation improvements
- UI consistency and layout fixes
- API data handling support

### Keagan Tan
- Forecast enhancements
- Screen navigation and state handling
- Testing and bug-fixing support

---

## New Concepts Applied
- **Cloud NoSQL Database (Firestore)** – user-specific data persistence
- **Background Processing (WorkManager)** – reliable weather alerts
- **Modern Notification Handling** – channels and runtime permissions
- **Jetpack Compose** – reactive, state-driven UI
- **Text-to-Speech** – accessibility enhancement

---

## Problems Encountered & Solutions
- Duplicate background tasks → solved using unique WorkManager jobs
- Notification permission issues → handled with runtime permission checks
- Location search failures → implemented fallback to nearest recognised area
- UI spacing and layout issues → resolved through Compose restructuring

---

## Appendices (To Be Added)
- Architecture diagrams
- UI screenshots
- Database / data flow diagrams
- User guide

---

## Demonstration
The application demonstration includes:
- Login and authentication
- Location-based weather retrieval
- Forecast viewing
- Favourite locations (cloud sync)
- Background weather notifications
- Text-to-Speech narration

---
