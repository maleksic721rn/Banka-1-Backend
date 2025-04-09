FROM golang:1.24.1-alpine AS builder
WORKDIR /app
COPY . .
RUN go install github.com/swaggo/swag/cmd/swag@latest

RUN go install
RUN swag init
RUN go build -o app

FROM alpine:latest
WORKDIR /app
COPY --from=builder /app/.env .env
COPY --from=builder /app/app .
COPY --from=builder /app/docs ./docs
COPY --from=builder /app/exchanges/exchanges.csv exchanges/exchanges.csv
COPY --from=builder /app/listings/futures/future_data.csv listings/futures/future_data.csv
EXPOSE 3000
CMD ["./app"]
            