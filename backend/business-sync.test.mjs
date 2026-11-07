import test from 'node:test';
import assert from 'node:assert/strict';

import {
  customerInput,
  expenseInput
} from './business-sync.mjs';

test('customer fields are trimmed and optional contact fields are supported', () => {
  assert.deepEqual(
    customerInput(
      {
        name: '  Thandi  ',
        phone: '  0710000000  '
      },
      'customer-1'
    ),
    {
      id: 'customer-1',
      name: 'Thandi',
      phone: '0710000000',
      email: ''
    }
  );
});

test('blank customer names are rejected', () => {
  assert.throws(
    () => customerInput(
      { name: '   ' },
      'customer-1'
    ),
    /Customer name/
  );
});

test('customer IDs cannot contain Firestore path separators', () => {
  assert.throws(
    () => customerInput(
      { name: 'Thandi' },
      'another/account'
    ),
    /Invalid record ID/
  );
});

test('expense amounts use integer cents', () => {
  const result = expenseInput(
    {
      description: 'Transport',
      category: 'Travel',
      amountCents: 12550,
      date: '2026-11-07'
    },
    'expense-1'
  );

  assert.equal(result.amountCents, 12550);
  assert.equal(result.date, '2026-11-07');
});

test('fractional cents and zero expenses are rejected', () => {
  for (const amountCents of [12.5, 0, -100]) {
    assert.throws(
      () => expenseInput(
        {
          description: 'Transport',
          category: 'Travel',
          amountCents,
          date: '2026-11-07'
        },
        'expense-1'
      ),
      /Expense amount/
    );
  }
});

test('impossible expense dates are rejected', () => {
  assert.throws(
    () => expenseInput(
      {
        description: 'Transport',
        category: 'Travel',
        amountCents: 100,
        date: '2026-02-30'
      },
      'expense-1'
    ),
    /Expense date/
  );
});