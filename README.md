# TimeWise — Mindful Screen-Time Management

**TimeWise** is an Android productivity application designed to encourage mindful app usage and help users stay focused on their daily goals. It helps users manage app-specific screen-time limits and receive gentle reminders when they open apps whose daily limits have been exhausted.

## Overview

Digital distractions can make it harder to focus on personal priorities. TimeWise aims to encourage healthier digital habits through screen-time monitoring, daily goals, and timely notifications.

Rather than blocking applications or displaying intrusive overlays, TimeWise uses a **gentle notification-based approach** to remind users about their screen-time limits.

## Key Features

- **App Time Limits** — Configure daily usage limits for selected applications.
- **Gentle Notifications** — Receive a reminder when opening a selected app whose daily time limit has been exhausted.
- **Goal Management** — Define daily goals and track task completion.
- **Usage Monitoring** — Monitor relevant app usage to determine whether configured limits have been reached.
- **User-Controlled Permissions** — Use Android system permissions to support relevant usage-monitoring functionality.

## How It Works

1. Select the applications whose usage you want to manage.
2. Configure daily screen-time limits and create personal goals.
3. Use your phone normally while TimeWise monitors relevant app activity.
4. When you open a selected app whose daily limit has been exhausted, TimeWise sends a gentle notification reminder.
5. Continue using your device without an intrusive overlay or mandatory interaction.

TimeWise is designed as a productivity companion, not an application blocker.

## Tech Stack

| Technology | Purpose |
|---|---|
| Kotlin | Primary programming language |
| Jetpack Compose | Modern declarative Android UI |
| Android SDK | Native Android functionality |
| Android Accessibility Service | Detect relevant app/window transitions, if enabled and required |
| Usage Stats API | Access app usage information with user permission |
| Android Notifications | Deliver gentle reminders |
| Gradle | Build and dependency management |

## Android Permissions

Depending on the implemented functionality, TimeWise may request:

- **Usage Access** — To access app usage information for screen-time monitoring.
- **Accessibility Service** — To detect relevant app/window transitions where required.
- **Notification Permission** — To deliver notifications on Android versions that require runtime notification permission.

Permissions are requested to support the app's functionality and require user approval where applicable.

## Getting Started

### Prerequisites

- Android Studio
- Android SDK compatible with the project configuration
- A compatible JDK
- An Android device or emulator

### Run Locally

1. Clone the repository:

   ```bash
   git clone https://github.com/priyanshucodes786/TimeWise.git
   ```

2. Open the `TimeWise` directory in Android Studio.
3. Allow Gradle sync to complete.
4. Run the application on a compatible Android device or emulator.
5. Grant the relevant permissions requested by the app.

Permission availability and behavior may vary by Android version and device manufacturer.

## Project Structure

```text
TimeWise/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/kashyap/timewise/
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── TimeWiseAccessibility.kt
│   │   │   │   └── ui/theme/
│   │   │   ├── res/
│   │   │   └── AndroidManifest.xml
│   │   ├── androidTest/
│   │   └── test/
│   └── build.gradle.kts
├── gradle/
├── build.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
└── settings.gradle.kts
```

## Engineering Highlights

- Native Android development using Kotlin.
- Modern UI development with Jetpack Compose.
- Integration with Android usage-monitoring APIs.
- Event-driven app usage and transition handling.
- Notification-based reminders designed to avoid interrupting the user's workflow.

## Current Status

TimeWise is an ongoing Android development project. Features and implementation details may evolve as development and testing continue.

## Future Improvements

- Screen-time analytics and usage trends.
- Improved goal scheduling and completion tracking.
- Customizable notification preferences.
- Testing across Android versions and device manufacturers.
- Battery-efficiency, accessibility, and privacy improvements.

## Author

**Priyanshu Kashyap**

- GitHub: [@priyanshucodes786](https://github.com/priyanshucodes786)
- Project: [TimeWise Repository](https://github.com/priyanshucodes786/TimeWise)

---

## Copyright

Copyright © 2026 Priyanshu Kashyap. All rights reserved.

The TimeWise source code, original design assets, documentation, and other original materials in this repository are protected by applicable copyright laws.

Unless expressly authorized by the copyright holder or permitted by applicable law, no permission is granted to reproduce, redistribute, modify, or commercially exploit these materials.

The general concept of mindful screen-time management is not claimed as exclusive property by this notice.

---

*Build mindful digital habits, one notification at a time.*
