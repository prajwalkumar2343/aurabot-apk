# Aura managed backend on Cloud Run

The managed Google path uses one Cloud Run service and one MongoDB database. The
same container serves HTTPS API requests and runs Aura's durable Mongo lease
worker. The worker arrangement requires one warm instance and instance-based CPU
allocation; queue leases and fencing tokens keep multiple instances safe.

Local mode does not contact this service.

## Required secrets

Create these Secret Manager secrets before deployment:

- `aura-mongo-url`: TLS MongoDB Atlas connection string for a least-privilege app user.
- `aura-jwt-secret`: at least 48 random bytes, encoded as a URL-safe string.
- `aura-agent-credential-key`: at least 32 random characters; used only to seal short-lived queued provider credentials.
- `aura-managed-gemini-api-key`: the Aura-owned, API-restricted Gemini key.

Never put these values in the image, Gradle properties, source control, or Cloud
Run's ordinary environment variables.

## Build and deploy

Run from `backend/`, replacing the uppercase placeholders:

```bash
gcloud builds submit \
  --tag REGION-docker.pkg.dev/PROJECT_ID/aura/backend:VERSION .

gcloud run deploy aura-backend \
  --image REGION-docker.pkg.dev/PROJECT_ID/aura/backend:VERSION \
  --region REGION \
  --service-account aura-backend@PROJECT_ID.iam.gserviceaccount.com \
  --allow-unauthenticated \
  --min 1 \
  --max 20 \
  --concurrency 16 \
  --cpu 2 \
  --memory 2Gi \
  --timeout 300 \
  --no-cpu-throttling \
  --set-env-vars ENVIRONMENT=production,DB_NAME=aura_assistant,COOKIE_SECURE=true,AGENT_EMBEDDED_WORKER=true,AGENT_CONTINUOUS_CPU=true,ACCESS_MIN=15,REFRESH_DAYS=30,GOOGLE_WEB_CLIENT_ID=YOUR_WEB_CLIENT_ID \
  --set-secrets MONGO_URL=aura-mongo-url:latest,JWT_SECRET=aura-jwt-secret:latest,AGENT_CREDENTIAL_KEY=aura-agent-credential-key:latest,MANAGED_GEMINI_API_KEY=aura-managed-gemini-api-key:latest
```

Public Cloud Run invocation is intentional because Android users do not have IAM
roles in the Google Cloud project. Every protected application route still
requires an Aura bearer token. `/api/auth/google` accepts only a Google token
whose signature, issuer, audience, expiry, verified email, and one-time nonce are
valid.

Configure the Android release with the same OAuth web client ID and the deployed
HTTPS URL:

```bash
./gradlew assembleRelease \
  -PauraBackendUrl=https://YOUR_CLOUD_RUN_DOMAIN \
  -PauraGoogleWebClientId=YOUR_WEB_CLIENT_ID
```

## MongoDB network boundary

Production should use MongoDB Atlas on Google Cloud with Private Service Connect
and Cloud Run Direct VPC egress. If a temporary public Atlas endpoint is used,
route Cloud Run through Cloud NAT with a reserved address and allow only that
address. Do not add `0.0.0.0/0` to the Atlas access list.

The database user needs access only to `DB_NAME`. Aura scopes every managed
document by `user_id`; cross-tenant query tests must remain part of CI.

## Operational requirements

- Grant the Cloud Run service account only Secret Manager Secret Accessor for the four secrets.
- Restrict the Gemini key to the Generative Language API and set quota/budget alerts.
- Keep Cloud Run request logs, but do not enable body or authorization-header logging.
- Alert on repeated 401/429 responses, worker lease expiry, provider 5xx responses, and Mongo connection saturation.
- Rotate `JWT_SECRET` only with a planned session invalidation. Rotate `AGENT_CREDENTIAL_KEY` only after the run queue drains.
