# BizManage SA - PROG7312 Final POE



BizManage SA is my native Android business management application for South African small businesses. It helps users manage inventory, record sales, maintain customer records and track expenses.



I developed the application using Kotlin and Jetpack Compose, with Room for local storage and a custom Node.js REST API connected to Cloud Firestore. The API is hosted on Microsoft Azure and accessed over HTTPS.



## Project links



- \*\*Source code:\*\* https://github.com/ndamase107/BizManage-SA-Final-POE

- \*\*Hosted API health check:\*\* https://bizmanage-sa-api-nsabwa03-cagmawcsaxgpg4e4.indiasouthcentral-01.azurewebsites.net/health

- \*\*API base URL:\*\* https://bizmanage-sa-api-nsabwa03-cagmawcsaxgpg4e4.indiasouthcentral-01.azurewebsites.net/v1/



I also recorded a \*\*5-minute, 33-second demonstration\*\* of the application running on a physical Android device. The recording accompanies my submission.



## Features



### Authentication and account separation



- Google sign-in using Firebase Authentication.

- Optional biometric authentication for a signed-in account.

- Separate local databases and cloud records for each account.

- Protected API endpoints that verify Firebase ID tokens.

- Cloud access based on the authenticated user's account.



### Inventory management



- Add and view products.

- Search by product name, category or barcode.

- Store cost prices, selling prices, quantities and minimum stock levels.

- Add stock to existing products.

- Identify products that have reached their minimum stock level.

- Upload and retrieve product photographs through Cloudinary.



### Sales management



- Record a sale against a selected product.

- Validate the requested quantity and available stock.

- Calculate sale values using integer cents.

- Deduct inventory when a sale is recorded.

- Detect price changes before accepting a sale.

- Reuse operation IDs safely so retries do not deduct stock twice.



### Customers and expenses



- Add and view customer records.

- Record expense descriptions, categories, dates and amounts.

- Save changes locally and synchronise supported records with the API.



### Offline storage and synchronisation



- Room stores business records on the device.

- An outbox preserves changes waiting to upload.

- WorkManager schedules synchronisation when a network connection is available.

- Temporary connection failures trigger retries.

- Stable operation IDs protect against duplicate uploads.

- Version checks prevent an older change from silently replacing newer cloud data.

- A conflict review screen lets the user compare local and cloud product information before choosing the cloud version.

- Synchronisation status and manual retry controls are available in Settings.



### Notifications



- Firebase Cloud Messaging provides business notifications.

- Users can manage notification permission and their business notification preference.

- The backend creates a low-stock alert when a sale reduces stock from above the minimum level to the minimum level or below.

- Device registration links notifications to the authenticated account.



### Language support



The project includes language resources and an app language selector for:



- English

- isiZulu

- Sesotho



Some interface text remains in English. Translation coverage is therefore a known limitation rather than a fully completed feature.



## Technology stack



| Area | Technology |

| --- | --- |

| Android language | Kotlin |

| Interface | Jetpack Compose and Material 3 |

| Navigation | Navigation Compose |

| Local database | Room |

| Background work | WorkManager |

| Authentication | Firebase Authentication and Google sign-in |

| Device authentication | Android biometrics |

| Networking | Retrofit and OkHttp |

| Backend | Node.js and Express |

| Cloud database | Cloud Firestore |

| Image storage | Cloudinary |

| Push notifications | Firebase Cloud Messaging |

| API hosting | Microsoft Azure App Service |

| Automated checks | GitHub Actions |

| Tests | JUnit and Node.js test runner |



## Project structure



```text

app/src/main/java/com/example/bizmanagezz/

├── data/

│   ├── api/          API interfaces and network models

│   ├── local/        Room entities, DAOs and database migrations

│   ├── repository/   Business data access and checkout operations

│   └── sync/         Synchronisation workers and queue handling

├── domain/           Business validation and sale calculations

├── ui/

│   ├── screens/      Application screens

│   ├── theme/        Application theme

│   └── viewmodel/    ViewModel components

└── MainActivity.kt



app/src/test/         Local Android unit tests

backend/              REST API and backend tests

.github/workflows/    Automated build, test and deployment workflows

```



The project separates interface, data access, persistence and business rules. Some screens still access repositories directly, so the migration to consistent MVVM across all screens remains incomplete.



## How synchronisation works



1\. The user creates or changes a supported record.

2\. The application saves the record and its queued operation locally.

3\. WorkManager runs synchronisation when its network requirements are met.

4\. The application sends the operation to the HTTPS API with a Firebase ID token.

5\. The API validates the request and reads or writes the authenticated account's Firestore records.

6\. After a successful response, the application acknowledges the queued operation and updates its local synchronisation state.



Product uploads and deletions use operation IDs and version checks. Sales also use a stable sale ID so repeating an already accepted request does not record the sale or deduct stock again.



A genuine conflict requires review. Retrying a request does not automatically overwrite the newer cloud record.



## Android setup



### Requirements



- Android Studio.

- JDK 21 for running the project's Gradle build.

- Android SDK Platform 37.1.

- An Android device running Android 7.1 / API 25 or later, or a suitable emulator.

- A Google account and Google Play services for Google sign-in.



### Open the project



1\. Clone this repository.

2\. Open the repository folder in Android Studio.

3\. Allow Gradle to synchronise.

4\. Confirm that the required Android SDK is installed.

5\. Select a device and run the `app` configuration.



The application ID is:



```text

com.example.bizmanagezz

```



### Firebase configuration



The Android application uses:



```text

app/google-services.json

```



This is the Firebase Android client configuration. It is different from a Firebase Admin service account key.



When building with a different signing key, register that key's SHA-1 fingerprint in the Firebase project and download an updated client configuration if required. A build created on another computer may use a different debug signing key.



### Build and run unit tests



From the repository root on Windows:



```powershell

$env:JAVA\_HOME = "C:\\Program Files\\Android\\Android Studio\\jbr"

$env:Path = "$env:JAVA\_HOME\\bin;$env:Path"



.\\gradlew.bat assembleDebug testDebugUnitTest --console=plain

```



Adjust the Java path if Android Studio is installed elsewhere.



To install the debug build on a connected device:



```powershell

.\\gradlew.bat installDebug --console=plain

```



The generated debug APK is located at:



```text

app/build/outputs/apk/debug/app-debug.apk

```



The debug APK is a testing build, not a signed production release.



## Backend setup



### Requirements



- Node.js 24 LTS.

- npm.

- Access to the relevant Firebase project.

- Firebase Admin credentials for local development.

- Cloudinary credentials for product image uploads.



### Install dependencies



From the repository root:



```powershell

npm --prefix .\\backend ci

```



### Environment variables



| Variable | Purpose |

| --- | --- |

| `GOOGLE\_CLOUD\_PROJECT` | Firebase / Google Cloud project ID |

| `GOOGLE\_APPLICATION\_CREDENTIALS` | Local path to a Firebase Admin service account JSON file |

| `FIREBASE\_SERVICE\_ACCOUNT\_JSON` | Service account JSON supplied privately to the hosted backend |

| `CLOUDINARY\_CLOUD\_NAME` | Cloudinary cloud name |

| `CLOUDINARY\_API\_KEY` | Cloudinary API key |

| `CLOUDINARY\_API\_SECRET` | Cloudinary API secret |

| `NODE\_ENV` | Runtime environment, such as `production` |

| `PORT` | Port supplied by the hosting environment |



For local development, `GOOGLE\_APPLICATION\_CREDENTIALS` points to the Admin key stored outside the repository.



The Azure deployment uses `FIREBASE\_SERVICE\_ACCOUNT\_JSON` through its private application settings. It does not use a Windows file path.



Private keys and API secrets must not be placed in Android source files, the README or committed environment files.



### Start the API locally



After configuring the environment variables:



```powershell

npm --prefix .\\backend start

```



The local server normally listens on port 3000.



Check its health:



```powershell

Invoke-RestMethod "http://127.0.0.1:3000/health"

```



Expected response:



```json

{

&#x20; "status": "ok",

&#x20; "service": "BizManage SA API"

}

```



The Android application currently points to the hosted Azure API, so normal hosted testing does not require my local server to remain running.



## API overview



The API includes authenticated routes for:



- Listing, creating, updating and deleting products.

- Recording product-based sales.

- Reading supported sales records.

- Reading and writing customers.

- Reading and writing expenses.

- Registering notification devices.

- Sending a notification connection test.

- Uploading and retrieving product photographs.



Business endpoints are under `/v1/`. The `/health` endpoint is public.



Requests to protected endpoints require:



```text

Authorization: Bearer <Firebase ID token>

```



The backend derives account ownership from the verified token rather than trusting an account ID supplied in a request body.



## Testing



### Android unit tests



```powershell

.\\gradlew.bat testDebugUnitTest --console=plain

```



The sale rule tests cover monetary calculations and invalid sale conditions.



### Backend unit tests



```powershell

npm --prefix .\\backend test

```



The backend tests cover areas including:



- Integer-cent calculations.

- Stock deduction and overselling.

- Changed product prices.

- Deleted products.

- Invalid quantities and prices.

- Record ID validation.

- Sale retry matching.

- Customer validation.

- Expense amounts and dates.

- Invalid and oversized image uploads.



### Live integration checks



The repository includes:



```text

backend/verify-cloud.mjs

backend/verify-product-retries.mjs

```



These checks require a running API and the appropriate private development credentials. Check their configured target URL before running them.



The recorded successful runs included:



- 11 live API checks.

- Four product retry checks.



These verified authentication rejection, Firestore read/write, account separation, sale retry protection, stock validation, stale updates, deletion tombstones and repeated product operations.



The verification scripts create temporary test accounts and records and attempt to clean them up after running.



### Manual testing



During development I checked:



- Google sign-in.

- Biometric access.

- Product creation and stock changes.

- Product-based sales.

- Offline changes followed by synchronisation.

- Customer and expense uploads to Firestore.

- Recovery after a temporary synchronisation failure.

- Product conflict review.

- Notification permission and FCM delivery.

- A real low-stock notification triggered by a sale.

- Connection from the Android application to the hosted Azure HTTPS API.



I also completed a physical Android device demonstration and recorded it for submission.



## GitHub Actions



The repository contains two project workflows:



### Android build and unit tests



Builds the debug application and runs local Android unit tests. Test reports are uploaded when available.



### Test and deploy BizManage SA API



Installs backend dependencies, runs backend tests, packages the backend and deploys it to Azure App Service.



Both project workflows have completed successfully.



The repository also contains an inherited Classroom autograding workflow. Its result is separate from the Android and backend project workflows.



## Hosting



The backend is hosted on Azure App Service using a Linux Node.js runtime and HTTPS.



It uses a Free F1 plan for this academic project. The service can stop when inactive and is subject to free-plan limits. Background notification dispatch may be delayed while the service is inactive.



A successful health response confirms that the API process is responding. Authenticated application checks are also needed to verify database access.



## Current limitations



- Some screens still contain hard-coded English text.

- MVVM is not applied consistently across every screen.

- Historical sales are not fully restored from the cloud to another device.

- Legacy amount-only sales and business profile settings remain local.

- Product photograph uploads require internet access and are not queued for offline upload.

- Photo upload and retrieval need to be checked explicitly on the final physical-device build.

- Product conflict recovery has been tested; pending-sale conflict scenarios need further verification.

- Archived conflict information does not yet have a dedicated export screen.

- The free hosting plan does not provide an always-running backend.

- A signed release build and its release signing configuration are separate from the current debug build.



## Submission evidence



My final submission should include:



- The public GitHub repository link.

- The physical-device demonstration recording.

- Build and test evidence.

- Screenshots of the main application features.

- Evidence of offline changes synchronising to Firestore.

- Evidence of the low-stock notification.

- The required written reflection and AI-use declaration.

- The final APK or other release artefacts required by the assignment.



## AI assistance



I used ChatGPT and Codex to assist with planning, code generation, debugging, testing guidance and documentation.



My work included integrating the supplied code, configuring Firebase, Cloudinary and Azure, running builds and tests, checking application behaviour and recording the device demonstration.



AI assistance does not replace the need to understand, verify and explain the submitted implementation. I will include the separate AI-use declaration required by the assignment.



## Documentation and references



I used the following official documentation when developing and configuring the project:



- Kotlin: https://kotlinlang.org/docs/home.html

- Jetpack Compose: https://developer.android.com/develop/ui/compose

- Android architecture: https://developer.android.com/topic/architecture

- Room: https://developer.android.com/training/data-storage/room

- WorkManager: https://developer.android.com/topic/libraries/architecture/workmanager

- Android biometrics: https://developer.android.com/identity/sign-in/biometric-auth

- Android app languages: https://developer.android.com/guide/topics/resources/app-languages

- Firebase Authentication: https://firebase.google.com/docs/auth/android/google-signin

- Firebase Admin SDK: https://firebase.google.com/docs/admin/setup

- Cloud Firestore: https://firebase.google.com/docs/firestore

- Firebase Cloud Messaging: https://firebase.google.com/docs/cloud-messaging

- Cloudinary Node.js integration: https://cloudinary.com/documentation/node\_integration

- Retrofit: https://square.github.io/retrofit/

- Express: https://expressjs.com/

- Azure App Service for Node.js: https://learn.microsoft.com/azure/app-service/quickstart-nodejs

- GitHub Actions: https://docs.github.com/actions


## Final submission downloads

- [Signed release APK](https://drive.google.com/file/d/1lbG72bfC1vqIBXaRH5hBQYrCx6_FtwGH/view?usp=drive_link)
- [Physical Android device demonstration — 5 minutes 33 seconds](https://drive.google.com/file/d/1A0ecMg_jIXK31J2VCxyhLz_a96rvedPM/view?usp=sharing)

The demonstration shows the tested development build. The signed release APK built successfully but has not yet been tested on a device.