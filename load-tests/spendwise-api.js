import http from 'k6/http';
import { check, sleep } from 'k6';

const baseUrl = __ENV.SPENDWISE_BASE_URL || 'http://localhost:8080';
const sessionCookie = __ENV.SPENDWISE_SESSION_COOKIE;
const apiPath = __ENV.SPENDWISE_LOAD_PATH || '/api/v1/shopping/search-history?size=20';

export const options = {
  stages: [
    { duration: '30s', target: 10 },
    { duration: '1m', target: 40 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
  },
};

export default function () {
  const response = http.get(`${baseUrl}${apiPath}`, {
    headers: sessionCookie ? { Cookie: sessionCookie } : {},
  });
  check(response, {
    'authenticated API request succeeds': (res) => res.status === 200,
  });
  sleep(0.2);
}
