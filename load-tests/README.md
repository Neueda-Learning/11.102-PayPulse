# Load Tests (M2)

This folder contains a k6 script to pressure test the payment create flow and observe rate-limit and resilience behavior.

## Prerequisites

- k6 installed (https://k6.io/docs/get-started/installation/)
- Backend running locally on port 8080

## Run

```powershell
k6 run .\load-tests\payments-burst.js
```

Optional env overrides:

```powershell
$env:BASE_URL="http://localhost:8080/api/v1"
$env:SOURCE_ACCOUNT_ID="b2c3d4e5-1111-4a11-8a11-111111111111"
k6 run .\load-tests\payments-burst.js
```

## Notes

- The scenario pushes roughly 40,000 requests/minute (667/sec for 60s).
- Response check allows `200`, `201`, and `429` because rate limiting is expected at high load.

