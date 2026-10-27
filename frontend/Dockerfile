# ===== Estágio 1 — Build Angular =====
FROM node:20-alpine AS builder
WORKDIR /app
# Dependencias para node-gyp (lmdb compila C++ nativo)
RUN apk add --no-cache python3 make g++ gcc py3-pip
COPY package.json angular.json tsconfig.json tsconfig.app.json ./
RUN npm install
RUN apk add --no-cache chromium
COPY tsconfig.spec.json karma.conf.cjs ./
COPY src/ ./src/
RUN CHROME_BIN=/usr/bin/chromium npm test -- --watch=false --browsers=ChromeHeadlessNoSandbox
RUN npm run build -- --configuration production --base-href /cvforge/

# ===== Estágio 2 — Nginx estatico =====
FROM nginx:alpine
# Remove conteudo padrao do nginx
RUN rm -rf /usr/share/nginx/html/*

# Copia build do Angular
COPY --from=builder /app/dist/cvforge-frontend/browser/ /usr/share/nginx/html/

# Configuracao do nginx com suporte a HTML5 push-state (SPA) e proxied api
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

# daemon off para manter o container em primeiro plano
CMD ["nginx", "-g", "daemon off;"]
