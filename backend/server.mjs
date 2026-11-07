import { registerProductPhotos } from './product-photos.mjs';
import { queueLowStockAlert, startNotificationDispatcher } from './business-alerts.mjs';
import { registerNotifications } from './notifications.mjs';
import express from 'express';
import { createHash } from 'node:crypto';

import {
  initializeApp,
  applicationDefault
} from 'firebase-admin/app';

import { getAuth } from 'firebase-admin/auth';

import {
  getFirestore,
  FieldValue
} from 'firebase-admin/firestore';

import {
  ApiError,
  check,
  identifier,
  integer,
  productInput,
  saleInput,
  saleEffect,
  sameSale
} from './rules.mjs';

import {
  registerBusinessSync
} from './business-sync.mjs';

// Server credentials are supplied through the environment.
initializeApp({
  credential: applicationDefault()
});

const db = getFirestore();
const app = express();

app.disable('x-powered-by');
app.use(express.json({ limit: '64kb' }));

// Public health check.
app.get('/health', (_req, res) => {
  res.json({
    status: 'ok',
    service: 'BizManage SA API'
  });
});

// Protect all /v1 endpoints.
// Account ownership comes from the verified Firebase token.
app.use('/v1', async (req, res, next) => {
  const match = /^Bearer (\S+)$/.exec(
    req.get('authorization') || ''
  );

  if (!match) {
    return res.status(401).json({
      error: 'Sign-in required.'
    });
  }

  try {
    const decoded = await getAuth().verifyIdToken(
      match[1],
      true
    );

    req.owner = db
      .collection('users')
      .doc(decoded.uid);

    next();
  } catch (error) {
    if (
      String(error.code || '').startsWith('auth/')
    ) {
      return res.status(401).json({
        error: 'Invalid or expired session.'
      });
    }

    next(error);
  }
});

registerProductPhotos(app, db);

// Writes must contain a JSON object.
app.use('/v1', (req, _res, next) => {
  if (
    req.method === 'POST' ||
    req.method === 'PUT'
  ) {
    check(
      req.body &&
        typeof req.body === 'object' &&
        !Array.isArray(req.body),
      'A JSON object is required.'
    );
  }

  next();
});

/**
 * Prepare an optional product operation receipt.
 * A stable key makes a retry return the original result.
 */
function productOperation(req, details) {
  const rawKey = req.get('Idempotency-Key');

  if (rawKey === undefined) {
    return null;
  }

  const key = identifier(rawKey);

  const fingerprint = createHash('sha256')
    .update(JSON.stringify(details))
    .digest('hex');

  return {
    ref: req.owner
      .collection('sync_operations')
      .doc(key),
    fingerprint
  };
}

async function previousResult(
  transaction,
  operation
) {
  if (!operation) {
    return null;
  }

  const snapshot = await transaction.get(
    operation.ref
  );

  if (!snapshot.exists) {
    return null;
  }

  const receipt = snapshot.data();

  check(
    receipt.fingerprint === operation.fingerprint,
    'Operation ID already used for different details.',
    409
  );

  return receipt;
}

function saveReceipt(
  transaction,
  operation,
  result
) {
  if (!operation) {
    return;
  }

  transaction.create(operation.ref, {
    fingerprint: operation.fingerprint,
    result,
    createdAt: FieldValue.serverTimestamp()
  });
}

// Read products, including deletion tombstones.
app.get('/v1/products', async (req, res) => {
  const after = req.query.after;

  check(
    after === undefined ||
      typeof after === 'string',
    'Invalid page cursor.'
  );

  let query = req.owner
    .collection('products')
    .orderBy('id')
    .limit(100);

  if (after !== undefined) {
    query = query.startAfter(
      identifier(after)
    );
  }

  const snapshot = await query.get();

  res.json({
    items: snapshot.docs.map(
      document => document.data()
    ),
    nextCursor:
      snapshot.size === 100
        ? snapshot.docs.at(-1).id
        : null
  });
});

// Create or update a product using optimistic version checks.
app.put('/v1/products/:id', async (req, res) => {
  const product = productInput(
    req.body,
    req.params.id
  );

  const expectedVersion = integer(
    req.body.expectedVersion,
    'Expected version'
  );

  const ref = req.owner
    .collection('products')
    .doc(product.id);

  const operation = productOperation(req, {
    type: 'PRODUCT_PUT',
    id: product.id,
    name: product.name,
    category: product.category,
    costPriceCents: product.costPriceCents,
    sellingPriceCents: product.sellingPriceCents,
    quantity: product.quantity,
    minimumStock: product.minimumStock,
    barcode: product.barcode,
    expectedVersion
  });

  const saved = await db.runTransaction(
    async transaction => {
      const receipt = await previousResult(
        transaction,
        operation
      );

      if (receipt !== null) {
        return receipt.result;
      }

      const snapshot = await transaction.get(ref);

      const current = snapshot.exists
        ? snapshot.data()
        : null;

      const version = current?.version || 0;

      check(
        expectedVersion === version,
        'Product changed. Refresh before saving.',
        409
      );

      const updated = {
        ...product,
        deleted: false,
        version: version + 1
      };

      transaction.set(ref, {
        ...updated,
        updatedAt: FieldValue.serverTimestamp()
      });

      saveReceipt(
        transaction,
        operation,
        updated
      );

      return updated;
    }
  );

  res.json(saved);
});

// Store a tombstone instead of removing the product document.
app.delete('/v1/products/:id', async (req, res) => {
  const id = identifier(req.params.id);
  const rawVersion = req.query.expectedVersion;

  check(
    typeof rawVersion === 'string' &&
      /^\d+$/.test(rawVersion),
    'Expected version is required.'
  );

  const expectedVersion = integer(
    Number(rawVersion),
    'Expected version',
    1
  );

  const ref = req.owner
    .collection('products')
    .doc(id);

  const operation = productOperation(req, {
    type: 'PRODUCT_DELETE',
    id,
    expectedVersion
  });

  await db.runTransaction(
    async transaction => {
      const receipt = await previousResult(
        transaction,
        operation
      );

      if (receipt !== null) {
        return;
      }

      const snapshot = await transaction.get(ref);

      check(
        snapshot.exists,
        'Product not found.',
        404
      );

      const product = snapshot.data();

      check(
        product.version === expectedVersion,
        'Product changed. Refresh before deleting.',
        409
      );

      transaction.update(ref, {
        deleted: true,
        version: expectedVersion + 1,
        updatedAt: FieldValue.serverTimestamp()
      });

      saveReceipt(
        transaction,
        operation,
        {
          id,
          deleted: true,
          version: expectedVersion + 1
        }
      );
    }
  );

  res.status(204).end();
});

// A stable sale ID prevents repeated stock deductions.
app.post('/v1/sales', async (req, res) => {
  const request = saleInput(req.body);

  const saleRef = req.owner
    .collection('sales')
    .doc(request.id);

  const productRef = req.owner
    .collection('products')
    .doc(request.productId);

  const result = await db.runTransaction(
    async transaction => {
      const previous = await transaction.get(
        saleRef
      );

      if (previous.exists) {
        check(
          sameSale(previous.data(), request),
          'Sale ID already used for different details.',
          409
        );

        return {
          id: request.id,
          totalCents: previous.data().totalCents,
          replayed: true
        };
      }

      const snapshot = await transaction.get(
        productRef
      );

      const product = snapshot.exists
        ? snapshot.data()
        : null;

      const effect = saleEffect(
        product,
        request
      );

      const sale = {
        id: request.id,
        productId: request.productId,
        productName: product.name,
        quantity: request.quantity,
        unitPriceCents: product.sellingPriceCents,
        unitCostCents: product.costPriceCents,
        totalCents: effect.totalCents,
        customerName: request.customerName,
        createdAt: FieldValue.serverTimestamp()
      };

      transaction.create(saleRef, sale);

      transaction.update(productRef, {
        quantity: effect.remainingStock,
        version: product.version + 1,
        updatedAt: FieldValue.serverTimestamp()
      });

      queueLowStockAlert(
        transaction,
        db,
        req.owner.id,
        product,
        request,
        effect
      );

      return {
        id: request.id,
        totalCents: effect.totalCents,
        replayed: false
      };
    }
  );

  res
    .status(result.replayed ? 200 : 201)
    .json(result);
});

// Read cloud sales with pagination.
app.get('/v1/sales', async (req, res) => {
  const after = req.query.after;

  check(
    after === undefined ||
      typeof after === 'string',
    'Invalid page cursor.'
  );

  let query = req.owner
    .collection('sales')
    .orderBy('id')
    .limit(100);

  if (after !== undefined) {
    query = query.startAfter(
      identifier(after)
    );
  }

  const snapshot = await query.get();

  res.json({
    items: snapshot.docs.map(
      document => document.data()
    ),
    nextCursor:
      snapshot.size === 100
        ? snapshot.docs.at(-1).id
        : null
  });
});

// Authenticated customer and expense endpoints.
registerBusinessSync(app, db);
registerNotifications(app, db);
startNotificationDispatcher(db);

// Unknown endpoint.
app.use((_req, res) => {
  res.status(404).json({
    error: 'Endpoint not found.'
  });
});

// Central error handler.
app.use((error, _req, res, _next) => {
  if (error instanceof ApiError) {
    return res.status(error.status).json({
      error: error.message
    });
  }

  if (error.type === 'entity.parse.failed') {
    return res.status(400).json({
      error: 'Invalid JSON.'
    });
  }

  if (error.type === 'entity.too.large') {
    return res.status(413).json({
      error: 'Request too large.'
    });
  }

  // Do not log request tokens or private customer data.
  console.error(
    'API request failed:',
    error.code || error.name
  );

  res.status(503).json({
    error: 'Service unavailable. Retry later.'
  });
});

const port = Number(
  process.env.PORT || 3000
);

app.listen(port, '0.0.0.0', () => {
  console.log(
    `BizManage SA API listening on port ${port}`
  );
});