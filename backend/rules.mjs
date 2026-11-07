export class ApiError extends Error {
  constructor(status, message) {
    super(message);
    this.status = status;
  }
}

export function check(condition, message, status = 400) {
  if (!condition) throw new ApiError(status, message);
}

export function identifier(value) {
  check(typeof value === 'string' && /^[A-Za-z0-9_-]{1,128}$/.test(value), 'Invalid record ID.');
  return value;
}

export function text(value, label, max = 200, optional = false) {
  if (optional && (value === undefined || value === null)) return '';
  check(typeof value === 'string', `${label} must be text.`);
  const result = value.trim();
  check((optional || result.length > 0) && result.length <= max, `${label} is missing or too long.`);
  return result;
}

export function integer(value, label, min = 0, max = 1_000_000_000) {
  check(Number.isSafeInteger(value) && value >= min && value <= max, `${label} must be a whole number from ${min} to ${max}.`);
  return value;
}

export function productInput(body, id) {
  return {
    id: identifier(id),
    name: text(body.name, 'Name'),
    category: text(body.category, 'Category'),
    costPriceCents: integer(body.costPriceCents, 'Cost price'),
    sellingPriceCents: integer(body.sellingPriceCents, 'Selling price', 1),
    quantity: integer(body.quantity, 'Quantity', 0, 1_000_000),
    minimumStock: integer(body.minimumStock, 'Minimum stock', 0, 1_000_000),
    barcode: text(body.barcode, 'Barcode', 100, true)
  };
}

export function saleInput(body) {
  return {
    id: identifier(body.id),
    productId: identifier(body.productId),
    quantity: integer(body.quantity, 'Quantity', 1, 1_000_000),
    expectedPriceCents: integer(body.expectedPriceCents, 'Expected price', 1),
    customerName: text(body.customerName, 'Customer', 200, true) || null
  };
}

export function saleEffect(product, request) {
  check(product && !product.deleted, 'Product not found.', 404);
  check(product.sellingPriceCents === request.expectedPriceCents, 'Price changed. Refresh before selling.', 409);
  check(product.quantity >= request.quantity, 'Insufficient cloud stock.', 409);
  const totalCents = product.sellingPriceCents * request.quantity;
  check(Number.isSafeInteger(totalCents), 'Sale total is too large.');
  return { totalCents, remainingStock: product.quantity - request.quantity };
}

export function sameSale(existing, request) {
  return existing.productId === request.productId &&
    existing.quantity === request.quantity &&
    existing.unitPriceCents === request.expectedPriceCents &&
    existing.customerName === request.customerName;
}

