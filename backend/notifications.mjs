import { createHash, randomUUID } from 'node:crypto';
import { getMessaging } from 'firebase-admin/messaging';
import { FieldValue } from 'firebase-admin/firestore';
import { check, ApiError } from './rules.mjs';

function deviceToken(body) {
  check(
    typeof body.token === 'string' &&
      /^\S{20,4096}$/.test(body.token),
    'A valid device token is required.'
  );

  return body.token;
}

function tokenId(token) {
  return createHash('sha256')
    .update(token)
    .digest('hex');
}

export function registerNotifications(app, db) {
  app.post('/v1/notifications/register', async (req, res) => {
    const token = deviceToken(req.body);

    check(
      typeof req.body.enabled === 'boolean',
      'Notification preference is required.'
    );

    // One token has one current account binding.
    // Ownership always comes from the verified Firebase session.
    await db.collection('notification_devices')
      .doc(tokenId(token))
      .set(
        {
          token,
          accountId: req.owner.id,
          enabled: req.body.enabled,
          updatedAt: FieldValue.serverTimestamp()
        },
        { merge: true }
      );

    res.json({ registered: true });
  });

  app.post('/v1/notifications/test', async (req, res) => {
    const token = deviceToken(req.body);

    const deviceRef = db
      .collection('notification_devices')
      .doc(tokenId(token));

    const controlRef = req.owner
      .collection('notification_control')
      .doc('test');

    await db.runTransaction(async transaction => {
      const device = await transaction.get(deviceRef);
      const control = await transaction.get(controlRef);

      check(
        device.exists &&
          device.data().accountId === req.owner.id,
        'Register this device for your account first.',
        403
      );

      check(
        device.data().enabled === true,
        'Business notifications are disabled.',
        403
      );

      const previous = control.exists
        ? control.data().lastRequestedAt || 0
        : 0;

      const now = Date.now();

      check(
        now - previous >= 30_000,
        'Wait 30 seconds before sending another test.',
        429
      );

      transaction.set(controlRef, {
        lastRequestedAt: now
      });
    });

    try {
      await getMessaging().send({
        token,
        data: {
          accountId: req.owner.id,
          eventId: randomUUID(),
          title: 'BizManage SA',
          body: 'Your business notifications are working.'
        },
        android: {
          priority: 'high',
          ttl: 120_000
        }
      });

      // FCM acceptance does not prove the device displayed it.
      res.status(202).json({ accepted: true });
    } catch (error) {
      console.error(
        'FCM send failed:',
        error.code || error.name
      );

      if (
        error.code === 'messaging/registration-token-not-registered' ||
        error.code === 'messaging/invalid-registration-token'
      ) {
        throw new ApiError(
          410,
          'Device token expired. Register the device again.'
        );
      }

      throw new ApiError(
        503,
        'FCM could not send the message. Check the server console.'
      );
    }
  });
}