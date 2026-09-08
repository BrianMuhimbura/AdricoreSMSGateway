# Adricore SMS Gateway — Sasula Collections API

**Audience:** Sasula / Service Cops integration team  
**Version:** 1.0  
**Date:** 8 September 2026

This document describes the APIs Sasula must call to look up a gateway partner (customer reference) and to post a successful collection. Payments credit the partner’s SMS wallet.

---

## 1. Overview

| Method | Purpose |
|---|---|
| Login | Obtain a JWT for the dedicated Sasula integration user |
| Query customer | Validate the customer reference (partner code) and return name, outstanding amount, and partner details |
| Post payment | Record a Sasula collection: top up the partner credit account and write a credit transaction |

All requests are `POST` with `Content-Type: application/json`. All Sasula business methods require a Bearer token.

**Customer reference** is the unique partner code issued by Adricore, for example `ADRS100002`.

---

## 2. Base URL

Replace the host with the environment you are given.

```
{BASE_URL}/api/v1
```

Examples:

| Environment | Base URL                           |
|-------------|------------------------------------|
| UAT         | `http://{{uat-host}}/api/v1`          |
| Production  | `https://{production-host}/api/v1` |

---

## 3. Authentication

Adricore will create a dedicated integration user (role **Sasula Integration**) and share the username and password securely. That user can only call these collection APIs.

### 3.1 Login

**URL:** `POST {BASE_URL}/Auth/login/`

No `Authorization` header.

**Request**

| Field | Type | Required | Description |
|---|---|---|---|
| `username` | string | Yes | Integration username |
| `password` | string | Yes | Integration password |

```json
{
  "username": "sasula",
  "password": "{provided-password}"
}
```

**Success (`returnCode` = 0)**

```json
{
  "returnCode": 0,
  "returnMessage": "Welcome back sasula",
  "returnObject": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "user": {
      "username": "sasula",
      "roleCode": "SASULA"
    }
  }
}
```

Use `returnObject.token` on every subsequent request:

```
Authorization: Bearer {token}
```

**Notes**

- Tokens are valid for **12 hours**.
- Logging in again issues a new token and **invalidates** the previous one. Keep a single token in use until it expires, then login again.
- Expired or missing tokens return `returnCode` `401` with `returnMessage` `AUTHENTICATION REQUIRED` or `TOKEN EXPIRED`.

### 3.2 Calling authenticated APIs

```
POST {BASE_URL}/Sasula/{action}/
Authorization: Bearer {token}
Content-Type: application/json
```

---

## 4. Standard response envelope

Every response is JSON in this shape:

| Field | Type | Description |
|---|---|---|
| `returnCode` | number | `0` = success. Any other value is a failure. |
| `returnMessage` | string | Human-readable result. On failure, this is the reason. |
| `returnObject` | object or `null` | Payload on success; usually `null` on failure. |

HTTP status is typically `200`. Always inspect `returnCode`, not only HTTP status.

### Return codes

| `returnCode` | Meaning |
|---|---|
| `0` | Success |
| `1` | Business validation error (unknown customer, inactive customer, bad amount, duplicate receipt, etc.) |
| `401` | Missing, invalid, or expired token |
| `403` | Authenticated but the user is not allowed to call Sasula APIs |
| `500` | Unexpected server error (`returnMessage` has detail) |

---

## 5. Query customer reference details

Validates the partner code and returns the details Sasula needs to present the collection.

**URL:** `POST {BASE_URL}/Sasula/queryCustomer/`

**Headers:** `Authorization: Bearer {token}`

### Input

| Field | Type | Required | Description |
|---|---|---|---|
| `customerReference` | string | Yes | Unique customer reference (Adricore partner code), e.g. `ADRS100002` |

`partnerCode` is accepted as an alias of `customerReference`.

```json
{
  "customerReference": "ADRS100002"
}
```

### Output (`returnObject`)

| Field | Type | Description |
|---|---|---|
| `customerReference` | string | Echo of the partner code |
| `customerName` | string | Partner / organisation name |
| `amountOutstanding` | number | Sum of **pending** billing invoices. `0` if none |
| `fixedAmount` | boolean | `true` if outstanding is greater than zero (pay that amount in full). `false` if outstanding is `0` (any top-up amount is allowed) |
| `businessReference` | string | Same as the partner code; identifies the wallet the collection credits |
| `partnerId` | string (UUID) | Internal partner id |
| `partnerCode` | string | Same as `customerReference` |
| `email` | string | Partner email, if set |
| `phone` | string | Partner phone, if set |
| `status` | string | `active` |
| `currency` | string | Wallet currency, e.g. `UGX` |

**Success example**

```json
{
  "returnCode": 0,
  "returnMessage": "Customer found",
  "returnObject": {
    "customerReference": "ADRS100002",
    "customerName": "Example Partner Ltd",
    "amountOutstanding": 0,
    "fixedAmount": false,
    "businessReference": "ADRS100002",
    "partnerId": "e87ca817-050a-4965-a9fe-254cfeb35314",
    "partnerCode": "ADRS100002",
    "email": "billing@example.com",
    "phone": "+256700000000",
    "status": "active",
    "currency": "UGX"
  }
}
```

**Outstanding amount**

- Taken from pending rows in `billing_invoices` for that partner.
- If the partner has no pending invoices, `amountOutstanding` is `0` and `fixedAmount` is `false`.
- If there are pending invoices, `amountOutstanding` is their total and `fixedAmount` is `true`.

**Failure examples**

| Situation | `returnCode` | `returnMessage` |
|---|---|---|
| Missing reference | `1` | Customer reference cannot be empty |
| Unknown code | `1` | Unknown customer reference |
| Inactive partner | `1` | Customer reference is inactive |
| No / bad token | `401` | AUTHENTICATION REQUIRED |

---

## 6. Post payment

Called after Sasula has collected funds. The gateway:

1. Validates the customer reference (active partner).
2. Rejects duplicate Sasula receipt numbers.
3. Credits the partner’s credit account by `amountPaid`.
4. Inserts a `TOPUP` credit transaction (receipt stored as the transaction reference).

**URL:** `POST {BASE_URL}/Sasula/postPayment/`

**Headers:** `Authorization: Bearer {token}`

### Input

| Field | Type | Required | Description |
|---|---|---|---|
| `customerReference` | string | Yes | Partner code, e.g. `ADRS100002` |
| `amountPaid` | number or string | Yes | Amount collected. Must be greater than zero |
| `sasulaReceiptNumber` | string | Yes | Unique receipt on Sasula. Used for duplicate detection |
| `paymentChannel` | string | No | Where payment was made, e.g. `Centenary Bank`, `MTN mobile money` |
| `thirdPartyTransactionReference` | string | No | Transaction id in Sasula / third party |
| `channelTransactionId` | string | No | Transaction id on the source channel |
| `datePaid` | string | No | Date/time paid (ISO-8601 recommended) |
| `transactionDescription` | string | No | Narrative |
| `payerNumber` | string | No | Payer MSISDN or account, if the channel provides it |
| `payerName` | string | No | Payer name, if the channel provides it |

```json
{
  "customerReference": "ADRS100002",
  "amountPaid": 50000,
  "paymentChannel": "MTN mobile money",
  "thirdPartyTransactionReference": "TP-123456",
  "channelTransactionId": "CH-987654",
  "sasulaReceiptNumber": "SAS-20260908-0001",
  "datePaid": "2026-09-08T14:30:00",
  "transactionDescription": "SMS wallet top-up",
  "payerNumber": "256700000000",
  "payerName": "Jane Okello"
}
```

### Output (`returnObject`)

| Field | Type | Description |
|---|---|---|
| `customerReference` | string | Partner code |
| `customerName` | string | Partner name |
| `amountPaid` | number | Amount credited |
| `balanceAfter` | number | Wallet balance after this top-up |
| `transactionId` | string (UUID) | Gateway credit-transaction id |
| `sasulaReceiptNumber` | string | Echo of the receipt |
| `reference` | string | Stored transaction reference (the Sasula receipt) |
| `currency` | string | Wallet currency |

**Success example**

```json
{
  "returnCode": 0,
  "returnMessage": "Payment posted",
  "returnObject": {
    "customerReference": "ADRS100002",
    "customerName": "Example Partner Ltd",
    "amountPaid": 50000,
    "balanceAfter": 75000,
    "transactionId": "0471674b-00ab-4c94-93d8-8a96d8a3dd6c",
    "sasulaReceiptNumber": "SAS-20260908-0001",
    "reference": "SAS-20260908-0001",
    "currency": "UGX"
  }
}
```

**Duplicates**

`sasulaReceiptNumber` must be unique. Posting the same receipt again returns:

```json
{
  "returnCode": 1,
  "returnMessage": "Duplicate Sasula receipt number",
  "returnObject": null
}
```

Treat that as already processed: do not retry with a new receipt for the same collection.

**Other failures**

| Situation | `returnCode` | `returnMessage` |
|---|---|---|
| Missing reference | `1` | Customer reference cannot be empty |
| Unknown / inactive partner | `1` | Unknown customer reference / Customer reference is inactive |
| Missing amount | `1` | Amount paid cannot be empty |
| Amount ≤ 0 | `1` | Amount paid must be greater than zero |
| Non-numeric amount | `1` | Amount paid must be a number |
| Missing receipt | `1` | Sasula receipt number cannot be empty |
| No / bad token | `401` | AUTHENTICATION REQUIRED |

---

## 7. Suggested flow

1. Login once; cache the token until it expires (12 hours).
2. On enquiry: **Query customer** with the partner code the payer quoted.
3. If `returnCode` is not `0`, show `returnMessage` and stop.
4. Present `customerName`, `amountOutstanding`, and `currency`.
   - If `fixedAmount` is `true`, collect exactly `amountOutstanding`.
   - If `fixedAmount` is `false`, collect any amount the payer chooses (wallet top-up).
5. After a successful collection: **Post payment** with the Sasula receipt number and channel details.
6. If post payment returns duplicate receipt, treat as success (already posted).
7. On `401`, login again and retry the same business request **once**.

---

## 8. cURL examples

Replace `{BASE_URL}`, `{username}`, `{password}`, and `{token}`.

**Login**

```bash
curl -s -X POST "{BASE_URL}/Auth/login/" \
  -H "Content-Type: application/json" \
  -d '{"username":"{username}","password":"{password}"}'
```

**Query customer**

```bash
curl -s -X POST "{BASE_URL}/Sasula/queryCustomer/" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {token}" \
  -d '{"customerReference":"ADRS100002"}'
```

**Post payment**

```bash
curl -s -X POST "{BASE_URL}/Sasula/postPayment/" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {token}" \
  -d '{
    "customerReference": "ADRS100002",
    "amountPaid": 50000,
    "paymentChannel": "MTN mobile money",
    "thirdPartyTransactionReference": "TP-123456",
    "channelTransactionId": "CH-987654",
    "sasulaReceiptNumber": "SAS-20260908-0001",
    "datePaid": "2026-09-08T14:30:00",
    "transactionDescription": "SMS wallet top-up",
    "payerNumber": "256700000000",
    "payerName": "Jane Okello"
  }'
```

---

## 9. Alternate request style

The gateway also accepts a single endpoint with `SERVICE` and `ACTION` in the body:

`POST {BASE_URL}/`

```json
{
  "SERVICE": "Auth",
  "ACTION": "login",
  "username": "sasula",
  "password": "{provided-password}"
}
```

```json
{
  "SERVICE": "Sasula",
  "ACTION": "queryCustomer",
  "customerReference": "ADRS100002"
}
```

```json
{
  "SERVICE": "Sasula",
  "ACTION": "postPayment",
  "customerReference": "ADRS100002",
  "amountPaid": 50000,
  "sasulaReceiptNumber": "SAS-20260908-0001"
}
```

The path style in sections 3–6 is preferred.

---

## 10. Credentials

Adricore operations will provision:

| Item | Value |
|---|---|
| Base URL | *to be provided per environment* |
| Username | *dedicated Sasula integration user* |
| Password | *shared out of band* |
| Role | `SASULA` (Sasula Integration) |

Do not use operator or partner-admin accounts. Rotate the password if it is exposed. Contact Adricore if tokens fail with `403 NOT AUTHORISED`.
