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

assert.equal(
  config.project_info.project_id,
  'bizmanage-sa',
  'Unexpected Firebase project.'
);

const android = config.client.find(
  item =>
    item.client_info.android_client_info.package_name ===
    'com.example.bizmanagezz'
);

const apiKey = android?.api_key?.[0]?.current_key;
assert.ok(apiKey, 'Android Firebase configuration has no API key.');

process.env.GOOGLE_APPLICATION_CREDENTIALS ||= join(
  process.env.LOCALAPPDATA,
  'BizManageSA',
  'secrets',
  'bizmanage-sa-firebase-adminsdk-fbsvc-653e39d7f0.json'
);

const app = initializeApp({
  credential: cert(JSON.parse(await readFile(process.env.GOOGLE_APPLICATION_CREDENTIALS, 'utf8'))),
  projectId: 'bizmanage-sa'
});

const auth = getAuth(app);
const db = getFirestore(app);
const accounts = [];
const base = 'http://localhost:3000';
let passed = 0;

async function request(path, token, method = 'GET', body) {
  const response = await fetch(base + path, {
    method,
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(body ? { 'Content-Type': 'application/json' } : {})
    },
    body: body ? JSON.stringify(body) : undefined,
    signal: AbortSignal.timeout(60000)
  });

  const data = response.status === 204
    ? null
    : await response.json();

  return { status: response.status, data };
}

async function account() {
  const uid = 'bizmanage-api-test-' + randomUUID();
  accounts.push(uid);

  const customToken = await auth.createCustomToken(uid);

  const response = await fetch(
    `https://identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken?key=${apiKey}`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        token: customToken,
        returnSecureToken: true
      }),
      signal: AbortSignal.timeout(60000)
    }
  );

  const result = await response.json();

  assert.equal(
    response.status,
    200,
    `Test login failed: ${result.error?.message || response.status}`
  );

  assert.ok(result.idToken, 'No Firebase ID token returned.');
  return result.idToken;
}

function pass(label) {
  passed++;
  console.log(`PASS ${passed}: ${label}`);
}

try {
  assert.equal((await request('/health')).status, 200);
  pass('API health');

  assert.equal((await request('/v1/products')).status, 401);
  pass('Unauthenticated requests rejected');

  assert.equal(
    (await request('/v1/products', 'invalid-token')).status,
    401
  );
  pass('Invalid tokens rejected');

  const first = await account();
  const second = await account();

  const productId = 'test-bread';

  const product = {
    name: 'Integration test bread',
    category: 'Test',
    costPriceCents: 800,
    sellingPriceCents: 1250,
    quantity: 5,
    minimumStock: 2,
    barcode: '',
    expectedVersion: 0
  };

  const created = await request(
    `/v1/products/${productId}`,
    first,
    'PUT',
    product
  );

  assert.equal(created.status, 200, created.data?.error);
  assert.equal(created.data.version, 1);

  const listed = await request('/v1/products', first);
  assert.equal(listed.status, 200, listed.data?.error);
  assert.equal(
    listed.data.items.find(item => item.id === productId)?.quantity,
    5
  );
  pass('Authenticated Firestore write and read');

  const other = await request('/v1/products', second);
  assert.equal(other.status, 200, other.data?.error);
  assert.equal(other.data.items.length, 0);

  const otherWrite = await request(
    `/v1/products/${productId}`,
    second,
    'PUT',
    { ...product, quantity: 9 }
  );

  assert.equal(otherWrite.status, 200, otherWrite.data?.error);
  pass('Accounts have separate product records');

  const sale = {
    id: randomUUID(),
    productId,
    quantity: 2,
    expectedPriceCents: 1250,
    customerName: null
  };

  const sold = await request('/v1/sales', first, 'POST', sale);
  assert.equal(sold.status, 201, sold.data?.error);
  assert.equal(sold.data.totalCents, 2500);

  const replay = await request('/v1/sales', first, 'POST', sale);
  assert.equal(replay.status, 200, replay.data?.error);
  assert.equal(replay.data.replayed, true);

  const remaining = await request('/v1/products', first);
  assert.equal(
    remaining.data.items.find(item => item.id === productId)?.quantity,
    3
  );

  const sales = await request('/v1/sales', first);
  assert.equal(sales.status, 200, sales.data?.error);
  assert.equal(sales.data.items.length, 1);
  pass('Sale recorded once and stock deducted once after retry');

  const tooMany = await request(
    '/v1/sales',
    first,
    'POST',
    { ...sale, id: randomUUID(), quantity: 4 }
  );

  assert.equal(tooMany.status, 409);
  pass('Cloud overselling rejected');

  const reused = await request(
    '/v1/sales',
    first,
    'POST',
    { ...sale, quantity: 1 }
  );

  assert.equal(reused.status, 409);
  pass('Reusing a sale ID with different details rejected');

  const stale = await request(
    `/v1/products/${productId}`,
    first,
    'PUT',
    product
  );

  assert.equal(stale.status, 409);
  pass('Stale product update rejected');

  const isolated = await request('/v1/products', second);
  assert.equal(
    isolated.data.items.find(item => item.id === productId)?.quantity,
    9
  );
  pass('Another account stock remains unchanged');

  const deleted = await request(
    `/v1/products/${productId}?expectedVersion=2`,
    first,
    'DELETE'
  );

  assert.equal(deleted.status, 204, deleted.data?.error);

  const afterDelete = await request('/v1/products', first);
  assert.equal(
    afterDelete.data.items.find(item => item.id === productId)?.deleted,
    true
  );
  pass('Product deletion saved as sync tombstone');

  console.log(`All ${passed} live API checks passed.`);
} catch (error) {
  console.error('Live check failed:', error.message);
  process.exitCode = 1;
} finally {
  let clean = true;

  for (const uid of accounts) {
    assert.ok(/^bizmanage-api-test-[a-f0-9-]{36}$/.test(uid));

    try {
      await db.recursiveDelete(db.collection('users').doc(uid));
    } catch {
      clean = false;
      console.error('Could not remove temporary test records.');
    }

    try {
      await auth.deleteUser(uid);
    } catch (error) {
      if (error.code !== 'auth/user-not-found') {
        clean = false;
        console.error('Could not remove a temporary test account.');
      }
    }
  }

  if (clean) {
    console.log('Temporary test accounts and records cleaned up.');
  } else {
    process.exitCode = 1;
  }

  await db.terminate();
  await deleteApp(app);
}

