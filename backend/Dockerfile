FROM node:22-bookworm-slim AS node-dependencies

WORKDIR /build
COPY package.json package-lock.json ./
RUN npm ci

FROM python:3.11-slim-bookworm

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PORT=8080

RUN groupadd --system aura && useradd --system --gid aura --create-home aura

WORKDIR /app
COPY requirements.txt ./
RUN pip install --no-cache-dir --upgrade pip && \
    pip install --no-cache-dir -r requirements.txt

COPY --from=node-dependencies /usr/local/bin/node /usr/local/bin/node
COPY --from=node-dependencies /build/node_modules ./node_modules
COPY package.json package-lock.json ./
COPY app ./app

RUN chown -R aura:aura /app
USER aura

CMD ["sh", "-c", "exec uvicorn app.main:app --host 0.0.0.0 --port ${PORT} --proxy-headers --forwarded-allow-ips='*'"]
