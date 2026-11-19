FROM node:26-alpine

WORKDIR /app

# Como usamos apenas módulos nativos (node:sqlite, node:http, node:crypto, fetch global),
# não precisamos de dependências externas pesadas no npm install
COPY package.json ./
COPY src/ ./src/
COPY ../../web-tv-demo.html /app/web-tv-demo.html

EXPOSE 3333

ENV NODE_ENV=production
ENV PORT=3333
ENV DATABASE_PATH=/app/data/korp_tv.db

VOLUME ["/app/data"]

CMD ["node", "src/server.js"]
