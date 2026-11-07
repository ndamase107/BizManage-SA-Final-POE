import { createHash } from 'node:crypto';
import { FieldValue } from 'firebase-admin/firestore';

import {
  check,
  identifier,
  integer,
  text
} from './rules.mjs';

export function customerInput(body, id) {
  return {
    id: identifier(id),
    name: text(body.name, 'Customer name'),
    phone: text(body.phone, 'Phone', 100, true),
    email: text(body.email, 'Email', 200, true)
  };
}

export function expenseInput(body, id) {
  const date = text(body.date, 'Expense date', 10);

  check(
    /^\d{4}-\d{2}-\d{2}$/.test(date),
    'Expense date must use yyyy-MM-dd.'
  );

  const parsed = new Date(`${date}T00:00:00Z`);

  check(
    !Number.isNaN(parsed.getTime()) &&
      parsed.toISOString().slice(0, 10) === date,
    'Expense date is invalid.'
  );

  return {
    id: identifier(id),
    description: text(body.description, 'Description'),
    category: text(body.category, 'Category'),
    amountCents: integer(
      body.amountCents,
      'Expense amount',
      1
    ),
    date
  };
}

export function registerBusinessSync(app, db) {
  registerCollection(
    app,
    db,
    'customers',
    customerInput
  );

  registerCollection(
    app,
    db,
    'expenses',
    expenseInput
  );
}

function registerCollection(app, db, collection, validate) {
  app.get(`/v1/${collection}`, async (req, res) => {
    const after = req.query.after;

    check(
      after === undefined || typeof after === 'string',
      'Invalid page cursor.'
    );

    let query = req.owner
      .collection(collection)
      .orderBy('id')
      .limit(100);

    if (after !== undefined) {
      query = query.startAfter(identifier(after));
    }

    const snapshot = await query.get();

    res.json({
      items: snapshot.docs.map(document => document.data()),
      nextCursor:
        snapshot.size === 100
          ? snapshot.docs.at(-1).id
          : null
    });
  });

  app.put(`/v1/${collection}/:id`, async (req, res) => {
    const record = validate(req.body, req.params.id);

    const expectedVersion = integer(
      req.body.expectedVersion,
      'Expected version'
    );

    // Each queued change must retain this ID across retries.
    const operationId = identifier(
      req.get('Idempotency-Key')
    );

    const fingerprint = createHash('sha256')
      .update(
        JSON.stringify({
          collection,
          record,
          expectedVersion
        })
      )
      .digest('hex');

    const recordRef = req.owner
      .collection(collection)
      .doc(record.id);

    const operationRef = req.owner
      .collection('sync_operations')
      .doc(operationId);

    const result = await db.runTransaction(
      async transaction => {
        const previous = await transaction.get(
          operationRef
        );

        if (previous.exists) {
          const receipt = previous.data();

          check(
            receipt.fingerprint === fingerprint,
            'Operation ID already used for different details.',
            409
          );

          return receipt.result;
        }

        const snapshot = await transaction.get(
          recordRef
        );

        const currentVersion = snapshot.exists
          ? snapshot.data().version
          : 0;

        check(
          currentVersion === expectedVersion,
          'Cloud record changed. Review before saving.',
          409
        );

        const updated = {
          ...record,
          version: currentVersion + 1
        };

        transaction.set(recordRef, {
          ...updated,
          updatedAt: FieldValue.serverTimestamp()
        });

        transaction.create(operationRef, {
          fingerprint,
          result: updated,
          createdAt: FieldValue.serverTimestamp()
        });

        return updated;
      }
    );

    res.json(result);
  });
}