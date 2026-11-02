package handler

import (
	"context"
	"testing"
	"time"
)

func TestStartPushTaskWorkerReturnsImmediately(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	cancel()

	done := make(chan struct{})
	go func() {
		StartPushTaskWorker(ctx, time.Hour)
		close(done)
	}()

	select {
	case <-done:
	case <-time.After(time.Second):
		t.Fatal("StartPushTaskWorker blocked the caller")
	}
}
