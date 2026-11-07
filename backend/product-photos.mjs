import express from 'express';
import { createHash } from 'node:crypto';
import { v2 as cloudinary } from 'cloudinary';
import { FieldValue } from 'firebase-admin/firestore';

import {
  check,
  identifier,
  integer,
  ApiError
} from './rules.mjs';

export function validatePhoto(buffer, contentType) {
  check(
    Buffer.isBuffer(buffer) &&
      buffer.length > 0 &&
      buffer.length <= 3 * 1024 * 1024,
    'Choose an image smaller than 3 MB.',
    413
  );

  const mime = String(contentType || '')
    .split(';')[0]
    .trim()
    .toLowerCase();

  const jpeg =
    buffer.length >= 3 &&
    buffer[0] === 0xff &&
    buffer[1] === 0xd8 &&
    buffer[2] === 0xff;

  const png =
    buffer.length >= 8 &&
    buffer.subarray(0, 8).equals(
      Buffer.from([137, 80, 78, 71, 13, 10, 26, 10])
    );

  const webp =
    buffer.length >= 12 &&
    buffer.toString('ascii', 0, 4) === 'RIFF' &&
    buffer.toString('ascii', 8, 12) === 'WEBP';

  check(
    (mime === 'image/jpeg' && jpeg) ||
      (mime === 'image/png' && png) ||
      (mime === 'image/webp' && webp),
    'Choose a JPEG, PNG or WebP image.',
    415
  );
}

function configureCloudinary() {
  check(
    process.env.CLOUDINARY_CLOUD_NAME &&
      process.env.CLOUDINARY_API_KEY &&
      process.env.CLOUDINARY_API_SECRET,
    'Photo storage is not configured on the server.',
    503
  );

  cloudinary.config({
    cloud_name: process.env.CLOUDINARY_CLOUD_NAME,
    api_key: process.env.CLOUDINARY_API_KEY,
    api_secret: process.env.CLOUDINARY_API_SECRET,
    secure: true
  });
}

async function uploadPhoto(buffer, publicId) {
  const result = await new Promise((resolve, reject) => {
    const stream = cloudinary.uploader.upload_stream(
      {
        public_id: publicId,
        resource_type: 'image',
        overwrite: false,
        unique_filename: false,
        allowed_formats: ['jpg', 'jpeg', 'png', 'webp'],
        format: 'jpg',
        transformation: [
          {
            width: 1600,
            height: 1600,
            crop: 'limit'
          }
        ]
      },
      (error, uploaded) => {
        if (error) reject(error);
        else resolve(uploaded);
      }
    );

    stream.end(buffer);
  });

  // Some duplicate-upload responses contain only "existing".
  if (!result.secure_url) {
    return cloudinary.api.resource(publicId, {
      resource_type: 'image',
      type: 'upload'
    });
  }

  return result;
}

export function registerProductPhotos(app, db) {
  app.get('/v1/products/:id/photo', async (req, res) => {
    const id = identifier(req.params.id);

    const product = await req.owner
      .collection('products')
      .doc(id)
      .get();

    check(
      product.exists && !product.data().deleted,
      'Sync this product before adding a photo.',
      404
    );

    const photo = await req.owner
      .collection('product_photos')
      .doc(id)
      .get();

    res.json({
      photo: photo.exists ? photo.data() : null
    });
  });

  app.put(
    '/v1/products/:id/photo',
    express.raw({
      type: ['image/jpeg', 'image/png', 'image/webp'],
      limit: '3mb'
    }),
    async (req, res) => {
      const id = identifier(req.params.id);

      const operationId = identifier(
        req.get('Idempotency-Key')
      );

      const rawVersion = req.query.expectedVersion;

      check(
        typeof rawVersion === 'string' &&
          /^\d+$/.test(rawVersion),
        'Expected photo version is required.'
      );

      const expectedVersion = integer(
        Number(rawVersion),
        'Expected photo version'
      );

      validatePhoto(req.body, req.get('content-type'));

      const digest = createHash('sha256')
        .update(req.body)
        .digest('hex');

      const fingerprint = createHash('sha256')
        .update(
          JSON.stringify({
            id,
            expectedVersion,
            digest
          })
        )
        .digest('hex');

      const productRef = req.owner
        .collection('products')
        .doc(id);

      const photoRef = req.owner
        .collection('product_photos')
        .doc(id);

      const receiptRef = req.owner
        .collection('photo_operations')
        .doc(operationId);

      const previous = await receiptRef.get();

      if (previous.exists) {
        check(
          previous.data().fingerprint === fingerprint,
          'Photo operation ID was used for different details.',
          409
        );

        return res.json(previous.data().result);
      }

      // Check ownership, existence and version before uploading.
      const product = await productRef.get();
      const currentPhoto = await photoRef.get();

      check(
        product.exists && !product.data().deleted,
        'Sync this product before adding a photo.',
        404
      );

      check(
        (currentPhoto.exists
          ? currentPhoto.data().version
          : 0) === expectedVersion,
        'The product photo changed. Reload before uploading.',
        409
      );

      configureCloudinary();

      const accountFolder = createHash('sha256')
        .update(req.owner.id)
        .digest('hex');

      // Stable IDs prevent retries from creating duplicate copies.
      // Including the digest prevents different files sharing an asset.
      const publicId =
        `bizmanage/${accountFolder}/${id}/` +
        `${operationId}_${digest}`;

      let uploaded;

      try {
        uploaded = await uploadPhoto(req.body, publicId);
      } catch (error) {
        console.error(
          'Product photo upload failed:',
          error.http_code || error.name
        );

        throw new ApiError(
          502,
          'Photo storage could not accept the image.'
        );
      }

      check(
        typeof uploaded.secure_url === 'string' &&
          uploaded.secure_url.startsWith(
            'https://res.cloudinary.com/'
          ),
        'Photo storage returned an invalid image URL.',
        502
      );

      const result = await db.runTransaction(
        async transaction => {
          const receipt = await transaction.get(receiptRef);
          const latestProduct = await transaction.get(productRef);
          const latestPhoto = await transaction.get(photoRef);

          if (receipt.exists) {
            check(
              receipt.data().fingerprint === fingerprint,
              'Photo operation ID was used for different details.',
              409
            );

            return receipt.data().result;
          }

          check(
            latestProduct.exists &&
              !latestProduct.data().deleted,
            'The product was deleted.',
            409
          );

          const version = latestPhoto.exists
            ? latestPhoto.data().version
            : 0;

          check(
            version === expectedVersion,
            'The photo changed during upload. Reload it.',
            409
          );

          const saved = {
            productId: id,
            url: uploaded.secure_url,
            publicId: uploaded.public_id,
            width: uploaded.width,
            height: uploaded.height,
            version: version + 1
          };

          transaction.set(photoRef, {
            ...saved,
            updatedAt: FieldValue.serverTimestamp()
          });

          transaction.create(receiptRef, {
            fingerprint,
            result: saved,
            createdAt: FieldValue.serverTimestamp()
          });

          return saved;
        }
      );

      res.json(result);
    }
  );
}