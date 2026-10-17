FROM node:22-alpine3.23 AS build
WORKDIR /workspace
ARG VITE_TURNSTILE_SITE_KEY
ENV VITE_TURNSTILE_SITE_KEY=$VITE_TURNSTILE_SITE_KEY
RUN corepack enable
RUN corepack prepare pnpm@10.15.1 --activate
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile
COPY . .
RUN pnpm build

FROM nginx:1.31.6-alpine3.24
RUN apk add --no-cache tiff \
    && apk upgrade --no-cache libexpat tiff pcre2
COPY deploy/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist /usr/share/nginx/html
RUN mkdir -p /var/cache/nginx /var/run/nginx && chown -R nginx:nginx /var/cache/nginx /var/run/nginx /usr/share/nginx/html
EXPOSE 8080
USER nginx
