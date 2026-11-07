import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { join } from 'node:path';

import {
  initializeApp,
  cert,
  deleteApp
} from 'firebase-admin/app';

import { getAuth } from 'firebase-admin/auth';
import { getFirestore } from 'firebase-admin/firestore';

const config = JSON.parse(
  await readFile(
    new URL('../app/google-services.json', import.meta.url),
    'utf8'
  )
);

assert.equal(config.project_info.project_id, 'bizmanage-sa');

const apiKey = config.client
  .find(client =>
    client.client_info.android_client_info?.package_name ===
    'com.example.bizmanagezz'
  )
  ?.api_key?.[0]?.current_key;

assert.ok(apiKey, 'Android Firebase API key was not found.');

const keyPath =
  process.env.GOOGLE_APPLICATION_CREDENTIALS ||
  join(
    process.env.LOCALAPPDATA,
    'BizManageSA',
    'secrets',
    'bizmanage-sa-firebase-adminsdk-fbsvc-653e39d7f0.json'
  );

const credentials = JSON.parse(
  await readFile(keyPath, 'utf8')
);

assert.equal(credentials.type, 'service_account');
assert.equal(credentials.project_id, 'bizmanage-sa');

const app = initializeApp({
  credential: cert(credentials),
  projectId: 'bizmanage-sa'
});

const auth = getAuth(app);
const db = getFirestore(app);

const uid = `bizmanage-retry-test-${randomUUID()}`;
const productId = randomUUID();

let createdUser = false;

try {
  await auth.createUser({ uid });
  createdUser = true;

  const customToken = await auth.createCustomToken(uid);

  const signInResponse = await fetch(
    'https://identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken' +
      `?key=${encodeURIComponent(apiKey)}`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        token: customToken,
        returnSecureToken: true
      })
    }
  );

  assert.equal(
    signInResponse.status,
    200,
    'Temporary account sign-in failed.'
  );

  const { idToken } = await signInResponse.json();

  assert.ok(idToken, 'Sign-in returned no ID token.');

  async function request(method, operationId, body, query = '') {
    return fetch(
      `http://127.0.0.1:3000/v1/products/${productId}${query}`,
      {
        method,
        headers: {
          Authorization: `Bearer ${idToken}`,
          'Content-Type': 'application/json',
          'Idempotency-Key': operationId
        },
        ...(body === undefined
          ? {}
          : { body: JSON.stringify(body) })
      }
    );
  }

  const createId = randomUUID();

  const product = {
    name: 'Temporary retry test',
    category: 'Testing',
    costPriceCents: 500,
    sellingPriceCents: 1000,
    quantity: 5,
    minimumStock: 1,
    barcode: '',
    expectedVersion: 0
  };

  const first = await request('PUT', createId, product);
  assert.equal(first.status, 200);

  const firstProduct = await first.json();
  assert.equal(firstProduct.version, 1);

  const retry = await request('PUT', createId, product);
  assert.equal(retry.status, 200);
  assert.deepEqual(await retry.json(), firstProduct);

  console.log('PASS 1: Repeated product upload returns the same result');

  let stored = await db
    .collection('users')
    .doc(uid)
    .collection('products')
    .doc(productId)
    .get();

  assert.equal(stored.data().version, 1);
  assert.equal(stored.data().quantity, 5);

  console.log('PASS 2: Upload retry does not increase the product version');

  const changed = await request(
    'PUT',
    createId,
    { ...product, quantity: 99 }
  );

  assert.equal(changed.status, 409);

  console.log('PASS 3: Reusing an operation ID with different details is rejected');

  const deleteId = randomUUID();
  const query = '?expectedVersion=1';

  const deleted = await request(
    'DELETE',
    deleteId,
    undefined,
    query
  );

  assert.equal(deleted.status, 204);

  const repeatedDelete = await request(
    'DELETE',
    deleteId,
    undefined,
    query
  );

  assert.equal(repeatedDelete.status, 204);

  stored = await db
    .collection('users')
    .doc(uid)
    .collection('products')
    .doc(productId)
    .get();

  assert.equal(stored.data().deleted, true);
  assert.equal(stored.data().version, 2);

  console.log('PASS 4: Repeated deletion changes the product only once');
  console.log('All product retry checks passed.');
} catch (error) {
  console.error('Product retry check failed:', error.message);
  process.exitCode = 1;
} finally {
  try {
    // Delete only the temporary account created by this script.
    assert.match(
      uid,
      /^bizmanage-retry-test-[0-9a-f-]{36}$/
    );

    if (createdUser) {
      await db.recursiveDelete(
        db.collection('users').doc(uid)
      );

      await auth.deleteUser(uid);
      console.log('Temporary retry-test account and records cleaned up.');
    }
  } finally {
    await db.terminate();
    await deleteApp(app);
  }
}