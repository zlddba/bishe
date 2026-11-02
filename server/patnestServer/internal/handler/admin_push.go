package handler

import (
	"context"
	"fmt"
	"strconv"
	"strings"
	"time"

	"patnestServer/internal/db"

	"github.com/gin-gonic/gin"
)

// ---------------- 推送任务 ----------------

func AdminListPushTasks(c *gin.Context) {
	page, perPage, offset := paging(c, 100)
	status := c.Query("status")
	where := "WHERE ($1 = '' OR t.status = $1)"
	var total int
	_ = db.Pool.QueryRow(context.Background(),
		"SELECT COUNT(*) FROM notification_push_tasks t "+where, status).Scan(&total)

	rows, err := db.Pool.Query(context.Background(), `
		SELECT t.id, t.title, t.body, t.target, t.channel, t.status,
		       COALESCE(to_char(t.scheduled_at, 'YYYY-MM-DD HH24:MI'), ''),
		       COALESCE(to_char(t.sent_at, 'YYYY-MM-DD HH24:MI'), ''),
		       t.sent_count, COALESCE(t.error_message, ''), COALESCE(u.username, '系统'),
		       to_char(t.created_at, 'YYYY-MM-DD HH24:MI')
		FROM notification_push_tasks t LEFT JOIN users u ON u.id = t.creator_id `+where+`
		ORDER BY t.id DESC LIMIT $2 OFFSET $3`, status, perPage, offset)
	if err != nil {
		InternalError(c, "查询推送任务失败: "+err.Error())
		return
	}
	defer rows.Close()

	items := make([]gin.H, 0)
	for rows.Next() {
		var id, sentCount int64
		var title, body, target, channel, st, scheduled, sentAt, errMsg, creator, at string
		if err := rows.Scan(&id, &title, &body, &target, &channel, &st, &scheduled,
			&sentAt, &sentCount, &errMsg, &creator, &at); err != nil {
			continue
		}
		items = append(items, gin.H{
			"id": id, "title": title, "body": body, "target": target, "channel": channel,
			"status": st, "scheduled_at": scheduled, "sent_at": sentAt, "sent_count": sentCount,
			"error_message": errMsg, "creator": creator, "created_at": at,
		})
	}
	OK(c, gin.H{"items": items, "total": total, "page": page, "per_page": perPage})
}

type pushTaskInput struct {
	Title       string `json:"title" binding:"required,min=1,max=128"`
	Body        string `json:"body" binding:"required,min=1,max=2000"`
	Target      string `json:"target" binding:"omitempty,oneof=all owner sitter"`
	Channel     string `json:"channel" binding:"omitempty,oneof=in_app sms wechat"`
	ScheduledAt string `json:"scheduled_at" binding:"max=32"`
	SendNow     bool   `json:"send_now"`
}

func parsePushTime(raw string) (time.Time, error) {
	raw = strings.TrimSpace(raw)
	if raw == "" {
		return time.Time{}, nil
	}
	layouts := []string{"2006-01-02 15:04:05", "2006-01-02 15:04", time.RFC3339}
	for _, layout := range layouts {
		if t, err := time.ParseInLocation(layout, raw, time.Local); err == nil {
			return t, nil
		}
	}
	return time.Time{}, fmt.Errorf("定时发送时间格式应为 YYYY-MM-DD HH:mm:ss")
}

func AdminCreatePushTask(c *gin.Context) {
	var in pushTaskInput
	if err := c.ShouldBindJSON(&in); err != nil {
		BadRequest(c, "参数无效: "+err.Error())
		return
	}
	if in.Target == "" {
		in.Target = "all"
	}
	if in.Channel == "" {
		in.Channel = "in_app"
	}
	scheduled, err := parsePushTime(in.ScheduledAt)
	if err != nil {
		BadRequest(c, err.Error())
		return
	}
	if !scheduled.IsZero() && scheduled.Before(time.Now().Add(-time.Minute)) {
		BadRequest(c, "定时发送时间不能早于当前时间")
		return
	}

	status := "pending"
	var scheduledArg interface{}
	if !scheduled.IsZero() {
		scheduledArg = scheduled
		status = "scheduled"
	}
	if in.SendNow {
		scheduledArg = nil
		status = "pending"
	}

	var id int64
	err = db.Pool.QueryRow(context.Background(), `
		INSERT INTO notification_push_tasks (title, body, target, channel, status, scheduled_at, creator_id)
		VALUES ($1, $2, $3, $4, $5, $6, $7) RETURNING id`,
		in.Title, in.Body, in.Target, in.Channel, status, scheduledArg, c.GetInt64("user_id"),
	).Scan(&id)
	if err != nil {
		InternalError(c, "创建推送任务失败: "+err.Error())
		return
	}
	writeAudit(c, "CREATE_PUSH_TASK", "push_task", id,
		fmt.Sprintf(`{"target": %q, "channel": %q}`, in.Target, in.Channel))

	if in.SendNow || scheduled.IsZero() {
		count, sendErr := deliverPushTask(id)
		if sendErr != nil {
			InternalError(c, "任务已创建，但发送失败: "+sendErr.Error())
			return
		}
		Created(c, fmt.Sprintf("已推送 %d 位用户", count), gin.H{"id": id, "status": "sent", "sent": count})
		return
	}
	Created(c, "定时推送任务已创建", gin.H{"id": id, "status": "scheduled"})
}

func AdminSendPushTask(c *gin.Context) {
	id, _ := strconv.ParseInt(c.Param("id"), 10, 64)
	status, err := pushTaskStatus(id)
	if err != nil {
		NotFound(c, "推送任务不存在")
		return
	}
	if status == "sending" {
		Conflict(c, "任务正在发送中")
		return
	}
	if status == "sent" {
		Conflict(c, "任务已发送")
		return
	}
	if status == "cancelled" {
		Conflict(c, "任务已取消")
		return
	}
	count, err := deliverPushTask(id)
	if err != nil {
		InternalError(c, "发送失败: "+err.Error())
		return
	}
	writeAudit(c, "SEND_PUSH_TASK", "push_task", id, fmt.Sprintf(`{"count": %d}`, count))
	OK(c, gin.H{"id": id, "status": "sent", "sent": count})
}

func AdminCancelPushTask(c *gin.Context) {
	id, _ := strconv.ParseInt(c.Param("id"), 10, 64)
	tag, err := db.Pool.Exec(context.Background(), `
		UPDATE notification_push_tasks SET status = 'cancelled', updated_at = now()
		WHERE id = $1 AND status IN ('pending','scheduled')`, id)
	if err != nil {
		InternalError(c, "取消失败")
		return
	}
	if tag.RowsAffected() == 0 {
		Conflict(c, "任务已发送、正在发送或已取消")
		return
	}
	writeAudit(c, "CANCEL_PUSH_TASK", "push_task", id, "{}")
	OK(c, gin.H{"id": id, "status": "cancelled"})
}

func pushTaskStatus(id int64) (string, error) {
	var status string
	err := db.Pool.QueryRow(context.Background(),
		"SELECT status FROM notification_push_tasks WHERE id = $1", id).Scan(&status)
	return status, err
}

// deliverPushTask 原子领取任务并发送，避免多实例重复推送。
func deliverPushTask(id int64) (int64, error) {
	var title, body, target, channel string
	err := db.Pool.QueryRow(context.Background(), `
		UPDATE notification_push_tasks SET status = 'sending', updated_at = now()
		WHERE id = $1 AND status IN ('pending','scheduled','failed') RETURNING title, body, target, channel`,
		id).Scan(&title, &body, &target, &channel)
	if err != nil {
		return 0, fmt.Errorf("任务状态不可发送")
	}

	count, sendErr := sendPushContent(title, body, target, channel)
	if sendErr != nil {
		_, _ = db.Pool.Exec(context.Background(), `
			UPDATE notification_push_tasks SET status = 'failed', error_message = $1, updated_at = now()
			WHERE id = $2`, sendErr.Error(), id)
		return 0, sendErr
	}
	_, err = db.Pool.Exec(context.Background(), `
		UPDATE notification_push_tasks SET status = 'sent', sent_at = now(), sent_count = $1,
		       error_message = NULL, updated_at = now() WHERE id = $2`, count, id)
	if err != nil {
		return 0, err
	}
	return count, nil
}

func sendPushContent(title, body, target, channel string) (int64, error) {
	if channel == "in_app" {
		tag, err := db.Pool.Exec(context.Background(), `
			INSERT INTO notifications (user_id, type, title, body)
			SELECT id, 'system_announcement', $1, $2 FROM users
			WHERE deleted_at IS NULL AND status = 1 AND ($3 = 'all' OR role::text = $3)`,
			title, body, target)
		if err != nil {
			return 0, err
		}
		return tag.RowsAffected(), nil
	}
	// 演示环境没有接入短信/微信网关：统计目标人数并记录为模拟发送。
	var count int64
	err := db.Pool.QueryRow(context.Background(), `
		SELECT COUNT(*) FROM users
		WHERE deleted_at IS NULL AND status = 1 AND ($1 = 'all' OR role::text = $1)`,
		target).Scan(&count)
	return count, err
}

// ProcessDuePushTasks 由后台 worker 周期调用，发送到点的定时任务。
func ProcessDuePushTasks() {
	for i := 0; i < 20; i++ {
		var id int64
		err := db.Pool.QueryRow(context.Background(), `
			UPDATE notification_push_tasks SET updated_at = now()
			WHERE id = (
				SELECT id FROM notification_push_tasks
				WHERE status = 'scheduled' AND scheduled_at IS NOT NULL AND scheduled_at <= now()
				ORDER BY scheduled_at LIMIT 1
			) AND status = 'scheduled'
			RETURNING id`).Scan(&id)
		if err != nil {
			return
		}
		_, _ = deliverPushTask(id)
	}
}

// StartPushTaskWorker 每分钟扫描一次定时推送任务。
func StartPushTaskWorker(ctx context.Context, interval time.Duration) {
	go func() {
		ticker := time.NewTicker(interval)
		defer ticker.Stop()
		select {
		case <-ctx.Done():
			return
		case <-time.After(10 * time.Second):
		}
		ProcessDuePushTasks()
		for {
			select {
			case <-ctx.Done():
				return
			case <-ticker.C:
				ProcessDuePushTasks()
			}
		}
	}()
}
