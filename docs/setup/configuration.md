# Configuração

See `.env.example` for database, port, profile and seed settings. The application reads the database through
`DB_URL`, `DB_USERNAME` and `DB_PASSWORD`, the same contract used in Kubernetes. The schema is always validated
(`ddl-auto=validate`) after Flyway runs. Production-like environments must leave demonstration seeds disabled and
manage credentials externally.

Swagger UI is available at `http://localhost:8080/swagger-ui.html` after startup.

## E-mail Notifications (RF52)

The compose stack includes **Mailpit**, a local SMTP simulator, for demo and testing. The UI is available at
`http://localhost:8025` for inspecting e-mails sent by the application.

| Configuration | Environment Variable | Default | Note |
| --- | --- | --- | --- |
| **Channel** | `APP_NOTIFICATION_EMAIL_CHANNEL` | `smtp` | `log` uses simulated logging instead of SMTP. |
| **From address** | `APP_NOTIFICATION_EMAIL_FROM` | `no-reply@workshop.local` | Sender address in all outbound e-mails. |
| **Mail host** | `SPRING_MAIL_HOST` | `mailpit` | `mailpit` in docker-compose; update for cloud environments. |
| **Mail port** | `SPRING_MAIL_PORT` | `1025` | Mailpit SMTP port. For cloud (Kubernetes), use ConfigMap. |
| **Mail username** | `SPRING_MAIL_USERNAME` | *(empty)* | Mailpit requires no authentication. For production, store in Secret. |
| **Mail password** | `SPRING_MAIL_PASSWORD` | *(empty)* | Mailpit requires no authentication. For production, store in Secret. |

To disable SMTP notifications and use log-only mode, override `.env`:

```bash
APP_NOTIFICATION_EMAIL_CHANNEL=log docker compose up -d --build
```

For Kubernetes deployments:
- Add `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT` and `APP_NOTIFICATION_EMAIL_FROM` to the application **ConfigMap**.
- Add `SPRING_MAIL_USERNAME` and `SPRING_MAIL_PASSWORD` to a **Secret** (if required by your SMTP provider).
- Mailpit is not deployed to Kubernetes; use a production SMTP service and credentials instead.
