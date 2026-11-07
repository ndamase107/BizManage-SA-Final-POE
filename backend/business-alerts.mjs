import { createHash } from 'node:crypto';
import { FieldValue } from 'firebase-admin/firestore';
import { getMessaging } from 'firebase-admin/messaging';

/**
 * Called inside the sale transaction.
 * The sale and its notification job are committed together.
 */
export function queueLowStockAlert(
  transaction,
  db,
  accountId,
  product,
  sale,
  effect
) {
  const crossedThreshold =
    product.quantity > product.minimumStock &&
    effect.remainingStock <= product.minimumStock;

  if (!crossedThreshold) return;

  const eventId = createHash('sha256')
    .update(`${accountId}:${sale.id}:low-stock`)
    .digest('hex');

  transaction.create(
    db.collection('notification_jobs').doc(eventId),
    {
      accountId,
      eventId,
      title: 'Low stock alert',
      body:
        `${product.name}: ${effect.remainingStock} remaining. ` +
        `Minimum stock: ${product.minimumStock}.`,
      state: 'PENDING',
      attempts: 0,
      retryAt: 0,
      leaseUntil: 0,
      expiresAt: Date.now() + 3_600_000,
      createdAt: FieldValue.serverTimestamp()
    }
  );
}

let dispatcherStarted = false;
let dispatcherRunning = false;

/**
 * Dispatch durable notification jobs separately from the sale request.
 * Temporary FCM failures do not undo a successful sale.
 */
export function startNotificationDispatcher(db) {
  if (dispatcherStarted) return;
  dispatcherStarted = true;

  async function tick() {
    if (dispatcherRunning) return;
    dispatcherRunning = true;

    try {
      const jobs = await db
        .collection('notification_jobs')
        .where('state', '==', 'PENDING')
        .limit(50)
        .get();

      for (const document of jobs.docs) {
        await dispatchOne(db, document.ref);
      }
    } catch (error) {
      console.error(
        'Notification dispatcher failed:',
        error.code || error.name
      );
    } finally {
      dispatcherRunning = false;
    }
  }

  const timer = setInterval(tick, 15_000);
  timer.unref();

  void tick();
}

async function dispatchOne(db, ref) {
  const job = await db.runTransaction(async transaction => {
    const snapshot = await transaction.get(ref);

    if (!snapshot.exists) return null;

    const value = snapshot.data();
    const now = Date.now();

    if (value.state !== 'PENDING') return null;

    if (value.expiresAt <= now) {
      transaction.update(ref, {
        state: 'EXPIRED',
        leaseUntil: 0
      });

      return null;
    }

    if (
      value.retryAt > now ||
      value.leaseUntil > now
    ) {
      return null;
    }

    transaction.update(ref, {
      leaseUntil: now + 120_000,
      attempts: (value.attempts || 0) + 1
    });

    return value;
  });

  if (!job) return;

  try {
    const devices = await db
      .collection('notification_devices')
      .where('accountId', '==', job.accountId)
      .get();

    let eligibleDevices = 0;
    let temporaryFailure = false;

    for (const device of devices.docs) {
      // Re-read before sending in case the device changed accounts.
      const latest = await device.ref.get();
      const registration = latest.data();

      if (
        !latest.exists ||
        registration.accountId !== job.accountId ||
        registration.enabled !== true
      ) {
        continue;
      }

      eligibleDevices += 1;

      try {
        await getMessaging().send({
          token: registration.token,
          data: {
            accountId: job.accountId,
            eventId: job.eventId,
            title: job.title,
            body: job.body
          },
          android: {
            priority: 'high',
            ttl: 120_000
          }
        });
      } catch (error) {
        if (
          error.code ===
            'messaging/registration-token-not-registered' ||
          error.code === 'messaging/invalid-registration-token'
        ) {
          await db.runTransaction(async transaction => {
            const current = await transaction.get(device.ref);

            if (
              current.exists &&
              current.data().accountId === job.accountId &&
              current.data().token === registration.token
            ) {
              transaction.update(device.ref, {
                enabled: false
              });
            }
          });
        } else {
          temporaryFailure = true;

          console.error(
            'Business notification send failed:',
            error.code || error.name
          );
        }
      }
    }

    if (eligibleDevices === 0 || temporaryFailure) {
      await ref.update({
        leaseUntil: 0,
        retryAt: Date.now() + 30_000
      });
    } else {
      await ref.update({
        state: 'SENT',
        leaseUntil: 0,
        completedAt: FieldValue.serverTimestamp()
      });
    }
  } catch (error) {
    await ref.update({
      leaseUntil: 0,
      retryAt: Date.now() + 30_000
    });

    console.error(
      'Notification job deferred:',
      error.code || error.name
    );
  }
}