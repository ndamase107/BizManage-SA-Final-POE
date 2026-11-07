import test from 'node:test';
import assert from 'node:assert/strict';
import { validatePhoto } from './product-photos.mjs';

test('non-image data is rejected even with an image MIME type', () => {
  assert.throws(
    () => validatePhoto(
      Buffer.from('<script>test</script>'),
      'image/jpeg'
    ),
    /JPEG, PNG or WebP/
  );
});

test('SVG uploads are rejected', () => {
  assert.throws(
    () => validatePhoto(
      Buffer.from('<svg></svg>'),
      'image/svg+xml'
    ),
    /JPEG, PNG or WebP/
  );
});

test('empty uploads are rejected', () => {
  assert.throws(
    () => validatePhoto(
      Buffer.alloc(0),
      'image/png'
    ),
    /smaller than 3 MB/
  );
});

test('oversized uploads are rejected', () => {
  assert.throws(
    () => validatePhoto(
      Buffer.alloc(3 * 1024 * 1024 + 1),
      'image/jpeg'
    ),
    /smaller than 3 MB/
  );
});