FROM node:24-alpine AS construction
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci --no-fund --no-audit
COPY . .
RUN npm run build

FROM nginxinc/nginx-unprivileged:alpine
USER root
RUN apk upgrade --no-cache
USER 101
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=construction /src/dist /usr/share/nginx/html
HEALTHCHECK --interval=10s --timeout=3s CMD wget -qO- http://127.0.0.1:8080/ >/dev/null || exit 1
