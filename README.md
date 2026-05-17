# Macro Max

A nutrition and fitness tracking Android app built with Kotlin. Log meals, hit macro targets, track water intake, scan barcodes, and watch your progress — all in a clean, always-dark Material 3 UI.

---

## Features

### Onboarding
- Guided setup flow: age → weight → height → gender → goal
- Three goals: **Lose Weight**, **Maintain**, **Gain Weight**
- Macros calculated using the **Mifflin-St Jeor** formula (TDEE × 1.5, with ±400/700 kcal adjustments)

### Home Screen
- Time-based greeting (Good morning / afternoon / evening)
- **🔥 Streak counter** — tracks consecutive days with logged food
- Calorie donut with remaining vs. consumed at a glance
- Per-macro progress bars (protein · fat · carbs)
- Step counter with calories-burned estimate
- **Water tracker** — tap + / − to log glasses, progress bar vs. your custom daily goal

### Food Logging
- Search foods via the **Open Food Facts** API (2B+ products)
- **Barcode scanner** — point camera at any product to auto-fill nutrition info
- Manual entry fallback for anything not in the database
- **Meal types** — Breakfast, Lunch, Dinner, Snack (auto-selected by time of day)
- Edit or delete individual entries from the food list

### History
- **Weekly calorie bar chart** — colour-coded bars (green = on target, red = over, blue = under)
- Calendar to browse any past day
- Entries grouped by meal type for each day

### Profile
- Edit age, weight, height, gender, goal — targets recalculate instantly
- **Profile picture** — pick from gallery, saved locally
- Custom daily water goal
- View current macro targets (kcal / P / F / C)
- Logout with confirmation dialog

### Notifications
- Daily summary notification at 8 PM
- Shows remaining calories if under goal, or a congrats message if you hit it

### App Icon
- Adaptive icon: three-arc macro ring (green = protein, red = fat, blue = carbs) with a bold white **M** on a dark background

---

## Tech Stack

| Layer | Library / Tool |
|---|---|
| Language | Kotlin |
| UI | Material 3 (always-dark theme) |
| Auth | Firebase Authentication (Email, Google, Facebook, Guest) |
| Database | SharedPreferences (local), Cloud Firestore |
| Food Search | Open Food Facts REST API |
| Barcode Scanning | CameraX 1.3.1 + ML Kit Barcode Scanning 17.3.0 |
| Notifications | AlarmManager + BroadcastReceiver |
| Charts | Custom `Canvas`-based views (no charting library) |
| Build | AGP 9.1.1, minSdk 24, targetSdk 36 |

---

## Project Structure

```
app/src/main/java/com/example/macromax/
├── LoginActivity.kt            # Email / Google / Facebook / Guest sign-in
├── AgeSelectionActivity.kt     # Onboarding step 1
├── WeightSelectionActivity.kt  # Onboarding step 2
├── HeightSelectionActivity.kt  # Onboarding step 3
├── GenderSelectionActivity.kt  # Onboarding step 4
├── GoalSelectionActivity.kt    # Onboarding step 5
├── MacroResultActivity.kt      # Calculated targets reveal screen
├── MainActivity.kt             # Home dashboard
├── LogFoodActivity.kt          # Search + barcode + manual food entry
├── BarcodeScanActivity.kt      # CameraX barcode scanner
├── HistoryActivity.kt          # Calendar + weekly chart
├── ProfileActivity.kt          # User profile & settings
├── DailySummaryReceiver.kt     # 8 PM notification receiver
├── MacroMaxApp.kt              # Application class, notification channel
├── FoodEntry.kt                # Data model: logged food item
├── FoodLogAdapter.kt           # Grouped RecyclerView (meal headers + entries)
├── FoodSearchAdapter.kt        # Search results RecyclerView
├── FoodSearchResult.kt         # Data model: search API result
├── DayHistory.kt               # Data model: daily summary
├── HistoryDayAdapter.kt        # History list RecyclerView
├── MacroDonutView.kt           # Custom donut chart for macros
├── StepDonutView.kt            # Custom donut chart for steps
├── WeeklyCalorieChartView.kt   # Custom Canvas bar chart
└── ScanOverlayView.kt          # Camera viewfinder overlay
```

---

## Getting Started

### Prerequisites
- Android Studio Hedgehog or newer
- A Firebase project with **Authentication**, **Firestore**, and **Analytics** enabled

### Setup

1. **Clone the repo**
   ```bash
   git clone https://github.com/stojoskiantonio/macro-max.git
   cd macro-max
   ```

2. **Add Firebase config**  
   Download `google-services.json` from your Firebase project and place it at `app/google-services.json`.

3. **Add Facebook credentials** (optional)  
   In `app/src/main/res/values/strings.xml`, replace the placeholder values:
   ```xml
   <string name="facebook_app_id">YOUR_FACEBOOK_APP_ID</string>
   <string name="fb_login_protocol_scheme">fbYOUR_FACEBOOK_APP_ID</string>
   <string name="facebook_client_token">YOUR_FACEBOOK_CLIENT_TOKEN</string>
   ```

4. **Build & run**  
   Open in Android Studio and run on a device or emulator (API 24+).

---

## Permissions

| Permission | Used for |
|---|---|
| `CAMERA` | Barcode scanner |
| `ACTIVITY_RECOGNITION` | Step counter |
| `RECEIVE_BOOT_COMPLETED` | Re-schedule daily notification after reboot |
| `SCHEDULE_EXACT_ALARM` | 8 PM daily summary notification |

---

## Localization

The app ships with full string translations for:
- **English** (`values/strings.xml`)
- **Macedonian** (`values-mk/strings.xml`)

---

## License

This project is for personal/educational use.
