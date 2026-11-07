# BizManage SA custom API — first backend milestone

Server source for authenticated Firestore product CRUD and atomic sales. The Android app remains Kotlin. This server is Node.js, using Express and Firebase Admin.

## Setup

Use Node 22 or later. Run `npm install` and `npm test`. Keep the generated package-lock.json for reproducible deployment.

For local authenticated Firestore access, set GOOGLE_APPLICATION_CREDENTIALS to a private service-account JSON path, then run `npm start`. For Render, use a private secret file and point GOOGLE_APPLICATION_CREDENTIALS to it. Never commit a private key, paste it in chat, or package it in Android. The public Android google-services.json is not a server credential.

## Contract

GET /health is a liveness check only; it does not test Firebase connectivity. All /v1 endpoints require an Authorization: Bearer header containing a Firebase ID token. Server-side token verification chooses the account scope users/{verifiedUid}; the client cannot choose another owner.

- GET /v1/products?after=ID: up to 100 records, including deletion tombstones; follow nextCursor until null.
- PUT /v1/products/:id: name, category, costPriceCents, sellingPriceCents, quantity, minimumStock, barcode, expectedVersion. Create with version 0; later updates must use the last server version. Conflicts return 409, rather than silently overwriting stock. Prices are integer cents.
- DELETE /v1/products/:id?expectedVersion=N: creates a tombstone. Retrying with an old version returns 409; refresh and reconcile rather than blindly replaying.
- POST /v1/sales: id, productId, quantity, expectedPriceCents, optional customerName. The stable sale ID makes identical sale retries safe. Price, stock and conflicting reuse of IDs return 409. Server saves price/cost snapshots and deducts stock in a single Firestore transaction.
- GET /v1/sales?after=ID: paginated persisted sales.

## Status and limitations

The included eight unit tests cover validation and sale effects. They do not prove actual token enforcement, Firestore transaction behavior or cross-account isolation; integration tests are required before deployment. No hosted deployment, Android sync, customer/expense endpoints, image storage or push delivery exists yet.

Before connecting Android: add cloud versions and outbox state to Room; preserve a pre-sale cloud stock baseline; upload products before replaying local sales. Never upload an already-deducted local quantity and then replay its sales, which would double-deduct cloud stock. Reconcile 409 responses visibly. The client uses Firebase ID tokens, not Google OAuth ID tokens, to call this API.

Production Firestore client rules can remain deny-all because this server uses Admin credentials. Admin access bypasses those rules, so account checks in this server are essential. Backend credentials require appropriate Firestore and Firebase Authentication permissions.

References: https://firebase.google.com/docs/admin/setup ; https://firebase.google.com/docs/auth/admin/verify-id-tokens ; https://firebase.google.com/docs/firestore/manage-data/transactions

