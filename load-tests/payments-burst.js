import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    payment_burst: {
      executor: 'constant-arrival-rate',
      rate: 667,
      timeUnit: '1s',
      duration: '1m',
      preAllocatedVUs: 350,
      maxVUs: 1200,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.15'],
    http_req_duration: ['p(95)<1500'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/api/v1';
const ACCOUNT_ID = __ENV.SOURCE_ACCOUNT_ID || 'b2c3d4e5-1111-4a11-8a11-111111111111';

export default function () {
  const idempotencyKey = `${__VU}-${__ITER}-${Date.now()}`;
  const payload = JSON.stringify({
    sourceAccountId: ACCOUNT_ID,
    amount: 10.5,
    currency: 'INR',
    destinationAccount: 'ACC9001001',
    reference: 'k6-burst-test',
  });

  const res = http.post(`${BASE_URL}/payments`, payload, {
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': idempotencyKey,
    },
  });

  check(res, {
    'status is 201/200/429': (r) => [200, 201, 429].includes(r.status),
  });

  sleep(0.05);
}

