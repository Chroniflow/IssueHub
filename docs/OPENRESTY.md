# OpenResty 静态部署

Vue 前端是单页应用，构建产物只有一个 `index.html`。页面路径由 Vue Router 在浏览器端解析，因此 OpenResty 必须将非静态文件路径回退到 `index.html`。

```nginx
server {
    listen 80;
    server_name issue.simpmc.org;
    root /srv/simpissuehub/frontend;

    location /api/ {
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

将 `Frontend/dist` 内容复制到 `root` 指定目录后，执行 `openresty -t` 检查配置并 reload。这样 `/issues/123`、`/admin/users`、`/admin/categories` 等路径都会由同一个入口加载，刷新页面不会再返回 404。
