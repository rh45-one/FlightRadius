FROM node:20-alpine AS builder
WORKDIR /app
COPY frontend/package.json ./
RUN npm install
COPY frontend/tsconfig.json ./
COPY frontend/index.html ./
COPY frontend/src ./src
RUN npm run build

FROM nginx:alpine
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY nginx/certs /etc/nginx/certs
EXPOSE 80 443
CMD ["nginx", "-g", "daemon off;"]
