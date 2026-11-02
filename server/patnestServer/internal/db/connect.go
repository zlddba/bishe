package db

import (
	"context"
	"fmt"
	"log"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

var Pool *pgxpool.Pool

func Init(dsn string) error {
	cfg, err := pgxpool.ParseConfig(dsn)
	if err != nil {
		return fmt.Errorf("parse db config: %w", err)
	}

	cfg.MaxConns = 20
	cfg.MinConns = 2
	cfg.MaxConnLifetime = time.Hour
	cfg.MaxConnIdleTime = 30 * time.Minute

	// Register custom ENUM types so pgx can scan them into Go strings
	cfg.AfterConnect = func(ctx context.Context, conn *pgx.Conn) error {
		enumTypes := []string{
			"user_role",
			"pet_species",
			"pet_size",
			"sitter_type",
			"sitter_status",
			"order_status",
			"payment_status",
			"pay_method",
			"notification_type",
			"price_mode",
		}
		for _, name := range enumTypes {
			t, err := conn.LoadType(ctx, name)
			if err != nil {
				log.Printf("[DB] Warning: could not load type %s: %v", name, err)
				continue
			}
			conn.TypeMap().RegisterType(t)
		}
		return nil
	}

	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()

	pool, err := pgxpool.NewWithConfig(ctx, cfg)
	if err != nil {
		return fmt.Errorf("create pool: %w", err)
	}

	if err := pool.Ping(ctx); err != nil {
		return fmt.Errorf("ping db: %w", err)
	}

	Pool = pool
	return nil
}

func Close() {
	if Pool != nil {
		Pool.Close()
	}
}
