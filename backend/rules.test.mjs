import test from 'node:test';
import assert from 'node:assert/strict';
import { productInput, saleInput, saleEffect, sameSale, identifier } from './rules.mjs';

const product = { name: 'Bread', category: 'Food', costPriceCents: 800,
  sellingPriceCents: 1250, quantity: 5, minimumStock: 2, version: 1 };
const request = { id: 'sale-1', productId: 'bread', quantity: 2,
  expectedPriceCents: 1250, customerName: null };

test('sale uses integer cents and deducts the requested stock', () => {
  assert.deepEqual(saleEffect(product, request), { totalCents: 2500, remainingStock: 3 });
});
test('overselling is a conflict', () => {
  assert.throws(() => saleEffect(product, { ...request, quantity: 6 }), error => error.status === 409);
});
test('a price change is a conflict', () => {
  assert.throws(() => saleEffect(product, { ...request, expectedPriceCents: 1000 }), error => error.status === 409);
});
test('deleted products cannot be sold', () => {
  assert.throws(() => saleEffect({ ...product, deleted: true }, request), error => error.status === 404);
});
test('fractional quantities are rejected at the API boundary', () => {
  assert.throws(() => saleInput({ ...request, quantity: 1.5 }));
});
test('negative product prices are rejected', () => {
  assert.throws(() => productInput({ ...product, costPriceCents: -1 }, 'bread'));
});
test('record IDs cannot contain Firestore path separators', () => {
  assert.throws(() => identifier('other-user/products'));
});
test('same operation details match a stored sale', () => {
  assert.equal(sameSale({ ...request, unitPriceCents: 1250 }, request), true);
  assert.equal(sameSale({ ...request, quantity: 1, unitPriceCents: 1250 }, request), false);
});

