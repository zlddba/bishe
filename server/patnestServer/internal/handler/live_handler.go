package handler

import (
	"context"
	"crypto/rand"
	"encoding/hex"
	"fmt"
	"log"
	"os"
	"strconv"
	"strings"
	"time"

	"patnestServer/internal/db"
	"patnestServer/internal/model"

	"github.com/gin-gonic/gin"
)

// ---------------------------------------------------------------------------
// 直播（自建 SRS 推拉流，见 deploy/live/）
//
// 分工：后端只负责「谁在播、播什么、能不能播、被怎么处理」，
// 真正的音视频走 SRS：机构按 push_url 推 RTMP，观看端拉 HLS/FLV。
// stream_key 是推流路径里的随机串，拿到就能推流 —— 因此只回给开播机构本人和后台。
// ---------------------------------------------------------------------------

const (
	liveViewerWindowSQL = `now() - interval '90 seconds'`
	liveMaxMessageLen   = 200
)

// liveBase 读取推拉流地址前缀；没配置时退回本机默认端口（本地联调 SRS 用）。
// 默认端口与 deploy/live/srs.conf 一致：9201=RTMP，9202=HTTP(HLS/FLV)。
func liveBase(env, def string) string {
	v := strings.TrimSpace(os.Getenv(env))
	if v == "" {
		v = def
	}
	return strings.TrimRight(v, "/")
}

func liveRTMPBase() string { return liveBase("LIVE_RTMP_BASE", "rtmp://127.0.0.1:9201/live") }
func liveHLSBase() string  { return liveBase("LIVE_HLS_BASE", "http://127.0.0.1:9202/live") }
func liveFLVBase() string  { return liveBase("LIVE_FLV_BASE", "http://127.0.0.1:9202/live") }

// fillLiveURLs 按 stream_key 拼出推流与播放地址。
func fillLiveURLs(r *model.LiveRoom) {
	if r.StreamKey == "" {
		return
	}
	r.PushURL = liveRTMPBase() + "/" + r.StreamKey
	r.PlayRTMP = r.PushURL
	r.PlayHLS = liveHLSBase() + "/" + r.StreamKey + ".m3u8"
	r.PlayFLV = liveFLVBase() + "/" + r.StreamKey + ".flv"
}

func newStreamKey() string {
	buf := make([]byte, 12)
	if _, err := rand.Read(buf); err != nil {
		// 极端情况下退回时间戳，宁可可预测也不让开播失败
		return fmt.Sprintf("k%d", time.Now().UnixNano())
	}
	return hex.EncodeToString(buf)
}

type rowScanner interface {
	Scan(dest ...any) error
}

const liveRoomColumns = `r.id, r.sitter_id, s.name, s.cover_photo_url, r.title, r.cover_url, r.status,
	r.viewer_count, r.peak_viewers, r.total_views, r.started_at, r.ended_at, r.end_reason, r.ban_reason,
	r.created_at, r.stream_key,
	(SELECT COUNT(*) FROM live_viewers v
	  WHERE v.room_id = r.id AND v.last_seen_at > ` + liveViewerWindowSQL + `)`

func scanLiveRoom(row rowScanner) (model.LiveRoom, error) {
	var r model.LiveRoom
	var liveViewers int
	err := row.Scan(&r.ID, &r.SitterID, &r.SitterName, &r.SitterCover, &r.Title, &r.CoverURL, &r.Status,
		&r.ViewerCount, &r.PeakViewers, &r.TotalViews, &r.StartedAt, &r.EndedAt, &r.EndReason, &r.BanReason,
		&r.CreatedAt, &r.StreamKey, &liveViewers)
	if err != nil {
		return r, err
	}
	r.ViewerCount = liveViewers
	return r, nil
}

// hideStreamKey 观看端拿不到推流钥匙。
func hideStreamKey(r *model.LiveRoom) {
	r.StreamKey = ""
	r.PushURL = ""
}

// liveSitterGuard 返回机构 id；机构被封禁时直接把响应写完并返回 ok=false。
func liveSitterGuard(c *gin.Context) (sitterID int64, ok bool) {
	uid := c.GetInt64("user_id")
	var bannedAt *time.Time
	var reason *string
	err := db.Pool.QueryRow(context.Background(),
		`SELECT id, live_banned_at, live_ban_reason FROM sitters
		 WHERE user_id = $1 AND deleted_at IS NULL`, uid).Scan(&sitterID, &bannedAt, &reason)
	if err != nil {
		NotFound(c, "机构资料不存在")
		return 0, false
	}
	if bannedAt != nil {
		msg := "直播权限已被封禁"
		if reason != nil && strings.TrimSpace(*reason) != "" {
			msg += "：" + *reason
		}
		Forbidden(c, msg)
		return sitterID, false
	}
	return sitterID, true
}

// ---------------------------------------------------------------------------
// 观看端（任何登录用户）
// ---------------------------------------------------------------------------

// ListLiveRooms GET /api/v1/live
func ListLiveRooms(c *gin.Context) {
	page, perPage, offset := paging(c, 50)

	args := []interface{}{}
	where := "WHERE r.status = 'living'"
	if sid := c.Query("sitter_id"); sid != "" {
		if v, err := strconv.ParseInt(sid, 10, 64); err == nil && v > 0 {
			args = append(args, v)
			where += fmt.Sprintf(" AND r.sitter_id = $%d", len(args))
		}
	}

	var total int
	if err := db.Pool.QueryRow(context.Background(),
		"SELECT COUNT(*) FROM live_rooms r "+where, args...).Scan(&total); err != nil {
		InternalError(c, "查询直播列表失败")
		return
	}

	args = append(args, perPage, offset)
	rows, err := db.Pool.Query(context.Background(),
		`SELECT `+liveRoomColumns+`
		 FROM live_rooms r JOIN sitters s ON s.id = r.sitter_id `+where+
			fmt.Sprintf(` ORDER BY r.started_at DESC LIMIT $%d OFFSET $%d`, len(args)-1, len(args)), args...)
	if err != nil {
		log.Printf("[ListLiveRooms] %v", err)
		InternalError(c, "查询直播列表失败")
		return
	}
	defer rows.Close()

	items := make([]model.LiveRoom, 0)
	for rows.Next() {
		r, err := scanLiveRoom(rows)
		if err != nil {
			log.Printf("[ListLiveRooms] scan: %v", err)
			continue
		}
		// 先拼地址（需要 stream_key），再把推流钥匙从响应里抹掉，观看端只留 play_*
		fillLiveURLs(&r)
		hideStreamKey(&r)
		items = append(items, r)
	}
	OK(c, gin.H{"items": items, "total": total, "page": page, "per_page": perPage})
}

// GetLiveRoom GET /api/v1/live/:id
func GetLiveRoom(c *gin.Context) {
	roomID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		BadRequest(c, "无效的直播ID")
		return
	}
	r, err := loadLiveRoom(roomID)
	if err != nil {
		NotFound(c, "直播不存在")
		return
	}
	// 已结束的直播仍然可查（用于回显结束原因）；推流钥匙只在开播接口给机构本人
	fillLiveURLs(&r)
	hideStreamKey(&r)
	OK(c, gin.H{"room": r})
}

func loadLiveRoom(roomID int64) (model.LiveRoom, error) {
	return scanLiveRoom(db.Pool.QueryRow(context.Background(),
		`SELECT `+liveRoomColumns+`
		 FROM live_rooms r JOIN sitters s ON s.id = r.sitter_id WHERE r.id = $1`, roomID))
}

// JoinLiveRoom POST /api/v1/live/:id/join
// 进房兼心跳：客户端每 30s 调一次，90s 没调视为离开。
func JoinLiveRoom(c *gin.Context) {
	roomID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		BadRequest(c, "无效的直播ID")
		return
	}
	userID := c.GetInt64("user_id")

	tx, err := db.Pool.Begin(context.Background())
	if err != nil {
		InternalError(c, "事务错误")
		return
	}
	defer tx.Rollback(context.Background())

	var status string
	if err := tx.QueryRow(context.Background(),
		"SELECT status FROM live_rooms WHERE id = $1 FOR UPDATE", roomID).Scan(&status); err != nil {
		NotFound(c, "直播不存在")
		return
	}
	if status != "living" {
		Conflict(c, "直播已结束")
		return
	}

	var inserted bool
	if err := tx.QueryRow(context.Background(),
		`INSERT INTO live_viewers (room_id, user_id) VALUES ($1, $2)
		 ON CONFLICT (room_id, user_id) DO UPDATE SET last_seen_at = now()
		 RETURNING (xmax = 0)`, roomID, userID).Scan(&inserted); err != nil {
		InternalError(c, "进入直播间失败")
		return
	}

	var viewers int
	_ = tx.QueryRow(context.Background(),
		`SELECT COUNT(*) FROM live_viewers WHERE room_id = $1 AND last_seen_at > `+liveViewerWindowSQL,
		roomID).Scan(&viewers)
	if _, err := tx.Exec(context.Background(),
		`UPDATE live_rooms SET viewer_count = $1, peak_viewers = GREATEST(peak_viewers, $1),
		        total_views = total_views + CASE WHEN $2 THEN 1 ELSE 0 END, updated_at = now()
		 WHERE id = $3`, viewers, inserted, roomID); err != nil {
		InternalError(c, "更新观看人数失败")
		return
	}
	room, err := scanLiveRoom(tx.QueryRow(context.Background(),
		`SELECT `+liveRoomColumns+`
		 FROM live_rooms r JOIN sitters s ON s.id = r.sitter_id WHERE r.id = $1`, roomID))
	if err != nil {
		InternalError(c, "读取直播失败")
		return
	}
	if err := tx.Commit(context.Background()); err != nil {
		InternalError(c, "提交失败")
		return
	}
	hideStreamKey(&room)
	fillLiveURLs(&room)
	OK(c, gin.H{"room": room, "viewer_count": viewers})
}

// LeaveLiveRoom POST /api/v1/live/:id/leave
func LeaveLiveRoom(c *gin.Context) {
	roomID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		BadRequest(c, "无效的直播ID")
		return
	}
	if _, err := db.Pool.Exec(context.Background(),
		"DELETE FROM live_viewers WHERE room_id = $1 AND user_id = $2", roomID, c.GetInt64("user_id")); err != nil {
		InternalError(c, "离开直播间失败")
		return
	}
	var viewers int
	_ = db.Pool.QueryRow(context.Background(),
		`SELECT COUNT(*) FROM live_viewers WHERE room_id = $1 AND last_seen_at > `+liveViewerWindowSQL, roomID).
		Scan(&viewers)
	OK(c, gin.H{"viewer_count": viewers})
}

// ListLiveMessages GET /api/v1/live/:id/messages?after_id=0
func ListLiveMessages(c *gin.Context) {
	roomID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		BadRequest(c, "无效的直播ID")
		return
	}
	afterID, _ := strconv.ParseInt(c.Query("after_id"), 10, 64)

	rows, err := db.Pool.Query(context.Background(),
		`SELECT m.id, m.room_id, m.user_id,
		        COALESCE(NULLIF(u.nickname, ''), u.username), u.avatar_url, m.content, m.status, m.created_at
		 FROM live_messages m JOIN users u ON u.id = m.user_id
		 WHERE m.room_id = $1 AND m.status = 'visible' AND m.id > $2
		 ORDER BY m.id ASC LIMIT 100`, roomID, afterID)
	if err != nil {
		InternalError(c, "查询弹幕失败")
		return
	}
	defer rows.Close()

	items := make([]model.LiveMessage, 0)
	for rows.Next() {
		var m model.LiveMessage
		if err := rows.Scan(&m.ID, &m.RoomID, &m.UserID, &m.Nickname, &m.AvatarURL,
			&m.Content, &m.Status, &m.CreatedAt); err == nil {
			items = append(items, m)
		}
	}
	OK(c, gin.H{"items": items})
}

// PostLiveMessage POST /api/v1/live/:id/messages
func PostLiveMessage(c *gin.Context) {
	roomID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		BadRequest(c, "无效的直播ID")
		return
	}
	var in struct {
		Content string `json:"content" binding:"required"`
	}
	if err := c.ShouldBindJSON(&in); err != nil {
		BadRequest(c, "请输入弹幕内容")
		return
	}
	content := strings.TrimSpace(in.Content)
	if content == "" || len([]rune(content)) > liveMaxMessageLen {
		BadRequest(c, fmt.Sprintf("弹幕内容需在 1-%d 字之间", liveMaxMessageLen))
		return
	}

	var status string
	if err := db.Pool.QueryRow(context.Background(),
		"SELECT status FROM live_rooms WHERE id = $1", roomID).Scan(&status); err != nil {
		NotFound(c, "直播不存在")
		return
	}
	if status != "living" {
		Conflict(c, "直播已结束，无法发言")
		return
	}

	userID := c.GetInt64("user_id")
	var m model.LiveMessage
	err = db.Pool.QueryRow(context.Background(),
		`INSERT INTO live_messages (room_id, user_id, content) VALUES ($1, $2, $3)
		 RETURNING id, room_id, user_id, content, status, created_at`,
		roomID, userID, content).
		Scan(&m.ID, &m.RoomID, &m.UserID, &m.Content, &m.Status, &m.CreatedAt)
	if err != nil {
		InternalError(c, "发送弹幕失败")
		return
	}
	_ = db.Pool.QueryRow(context.Background(),
		`SELECT COALESCE(NULLIF(nickname, ''), username), avatar_url FROM users WHERE id = $1`, userID).
		Scan(&m.Nickname, &m.AvatarURL)
	Created(c, "已发送", m)
}

// ---------------------------------------------------------------------------
// 机构端（开播 / 下播）
// ---------------------------------------------------------------------------

// ListMyLiveRooms GET /api/v1/sitter/live
func ListMyLiveRooms(c *gin.Context) {
	sitterID, err := mySitterID(c.GetInt64("user_id"))
	if err != nil {
		NotFound(c, "机构资料不存在")
		return
	}

	var bannedAt *time.Time
	var banReason *string
	_ = db.Pool.QueryRow(context.Background(),
		`SELECT live_banned_at, live_ban_reason FROM sitters WHERE id = $1`, sitterID).
		Scan(&bannedAt, &banReason)

	rows, err := db.Pool.Query(context.Background(),
		`SELECT `+liveRoomColumns+`
		 FROM live_rooms r JOIN sitters s ON s.id = r.sitter_id
		 WHERE r.sitter_id = $1 ORDER BY r.id DESC LIMIT 20`, sitterID)
	if err != nil {
		InternalError(c, "查询直播记录失败")
		return
	}
	defer rows.Close()

	items := make([]model.LiveRoom, 0)
	for rows.Next() {
		r, err := scanLiveRoom(rows)
		if err != nil {
			continue
		}
		fillLiveURLs(&r)
		if r.Status != "living" {
			// 历史直播只保留回放信息，不再暴露推流钥匙
			r.PushURL = ""
			r.StreamKey = ""
		}
		items = append(items, r)
	}

	var current *model.LiveRoom
	for i := range items {
		if items[i].Status == "living" {
			current = &items[i]
			break
		}
	}
	OK(c, gin.H{
		"items":       items,
		"current":     current,
		"live_banned": bannedAt != nil,
		"ban_reason":  banReason,
		"banned_at":   bannedAt,
	})
}

// StartLive POST /api/v1/sitter/live
func StartLive(c *gin.Context) {
	sitterID, ok := liveSitterGuard(c)
	if !ok {
		return
	}
	var in struct {
		Title    string `json:"title" binding:"required,max=128"`
		CoverURL string `json:"cover_url"`
	}
	if err := c.ShouldBindJSON(&in); err != nil {
		BadRequest(c, "请填写直播标题（128 字以内）")
		return
	}
	title := strings.TrimSpace(in.Title)
	if title == "" {
		BadRequest(c, "请填写直播标题")
		return
	}

	var living int
	_ = db.Pool.QueryRow(context.Background(),
		"SELECT COUNT(*) FROM live_rooms WHERE sitter_id = $1 AND status = 'living'", sitterID).Scan(&living)
	if living > 0 {
		Conflict(c, "已有正在进行的直播，请先下播")
		return
	}

	var roomID int64
	if err := db.Pool.QueryRow(context.Background(),
		`INSERT INTO live_rooms (sitter_id, title, cover_url, stream_key)
		 VALUES ($1, $2, $3, $4) RETURNING id`,
		sitterID, title, nilIfEmpty(in.CoverURL), newStreamKey()).Scan(&roomID); err != nil {
		log.Printf("[StartLive] insert: %v", err)
		InternalError(c, "开播失败")
		return
	}
	// 插入后单独读一次：同一条 SQL 里 INSERT...CTE 的写入对后续 SELECT 不可见（同一快照）
	r, err := loadLiveRoom(roomID)
	if err != nil {
		log.Printf("[StartLive] load: %v", err)
		InternalError(c, "开播失败")
		return
	}
	fillLiveURLs(&r)
	Created(c, "开播成功，客户端可直接开播（推流地址也已下发，可兼容外部推流工具）", r)
}

// StopLive PUT /api/v1/sitter/live/:id/stop
func StopLive(c *gin.Context) {
	sitterID, err := mySitterID(c.GetInt64("user_id"))
	if err != nil {
		NotFound(c, "机构资料不存在")
		return
	}
	roomID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		BadRequest(c, "无效的直播ID")
		return
	}
	tag, err := db.Pool.Exec(context.Background(),
		`UPDATE live_rooms SET status = 'ended', ended_at = now(), end_reason = 'host_stop', updated_at = now()
		 WHERE id = $1 AND sitter_id = $2 AND status = 'living'`, roomID, sitterID)
	if err != nil {
		InternalError(c, "下播失败")
		return
	}
	if tag.RowsAffected() == 0 {
		Conflict(c, "直播不存在或已结束")
		return
	}
	_, _ = db.Pool.Exec(context.Background(), "DELETE FROM live_viewers WHERE room_id = $1", roomID)
	OK(c, gin.H{"id": roomID, "status": "ended"})
}

// ---------------------------------------------------------------------------
// 后台（查看 / 强制下播 / 封禁 / 弹幕治理）
// ---------------------------------------------------------------------------

// AdminListLive GET /api/v1/admin/live?status=&keyword=
func AdminListLive(c *gin.Context) {
	page, perPage, offset := paging(c, 100)

	args := []interface{}{}
	where := "WHERE 1=1"
	if st := strings.TrimSpace(c.Query("status")); st != "" {
		switch st {
		case "living", "ended", "banned":
			args = append(args, st)
			where += fmt.Sprintf(" AND r.status = $%d", len(args))
		default:
			BadRequest(c, "未知的直播状态")
			return
		}
	}
	if kw := strings.TrimSpace(c.Query("keyword")); kw != "" {
		args = append(args, "%"+kw+"%")
		where += fmt.Sprintf(" AND (s.name ILIKE $%d OR r.title ILIKE $%d)", len(args), len(args))
	}

	var total int
	if err := db.Pool.QueryRow(context.Background(),
		"SELECT COUNT(*) FROM live_rooms r JOIN sitters s ON s.id = r.sitter_id "+where, args...).Scan(&total); err != nil {
		InternalError(c, "统计失败")
		return
	}

	args = append(args, perPage, offset)
	rows, err := db.Pool.Query(context.Background(),
		`SELECT `+liveRoomColumns+`
		 FROM live_rooms r JOIN sitters s ON s.id = r.sitter_id `+where+
			fmt.Sprintf(` ORDER BY (r.status = 'living') DESC, r.id DESC LIMIT $%d OFFSET $%d`, len(args)-1, len(args)), args...)
	if err != nil {
		InternalError(c, "查询失败")
		return
	}
	defer rows.Close()

	items := make([]model.LiveRoom, 0)
	for rows.Next() {
		r, err := scanLiveRoom(rows)
		if err != nil {
			continue
		}
		fillLiveURLs(&r)
		items = append(items, r)
	}
	OK(c, gin.H{"items": items, "total": total, "page": page, "per_page": perPage})
}

// AdminGetLive GET /api/v1/admin/live/:id
func AdminGetLive(c *gin.Context) {
	roomID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		BadRequest(c, "无效的直播ID")
		return
	}
	r, err := loadLiveRoom(roomID)
	if err != nil {
		NotFound(c, "直播不存在")
		return
	}
	fillLiveURLs(&r)

	var liveBannedAt *time.Time
	var liveBanReason *string
	_ = db.Pool.QueryRow(context.Background(),
		`SELECT live_banned_at, live_ban_reason FROM sitters WHERE id = $1`, r.SitterID).
		Scan(&liveBannedAt, &liveBanReason)

	rows, err := db.Pool.Query(context.Background(),
		`SELECT m.id, m.room_id, m.user_id,
		        COALESCE(NULLIF(u.nickname, ''), u.username), u.avatar_url, m.content, m.status, m.created_at
		 FROM live_messages m JOIN users u ON u.id = m.user_id
		 WHERE m.room_id = $1 ORDER BY m.id DESC LIMIT 200`, roomID)
	messages := make([]model.LiveMessage, 0)
	if err == nil {
		defer rows.Close()
		for rows.Next() {
			var m model.LiveMessage
			if err := rows.Scan(&m.ID, &m.RoomID, &m.UserID, &m.Nickname, &m.AvatarURL,
				&m.Content, &m.Status, &m.CreatedAt); err == nil {
				messages = append(messages, m)
			}
		}
	}

	OK(c, gin.H{
		"room":            r,
		"messages":        messages,
		"sitter_banned":   liveBannedAt != nil,
		"live_ban_reason": liveBanReason,
		"live_banned_at":  liveBannedAt,
	})
}

// AdminStopLive PUT /api/v1/admin/live/:id/stop
func AdminStopLive(c *gin.Context) {
	roomID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		BadRequest(c, "无效的直播ID")
		return
	}
	var in struct {
		Reason string `json:"reason"`
	}
	_ = c.ShouldBindJSON(&in)
	reason := strings.TrimSpace(in.Reason)

	var sitterID int64
	var sitterUserID int64
	var title string
	if err := db.Pool.QueryRow(context.Background(),
		`SELECT r.sitter_id, s.user_id, r.title FROM live_rooms r JOIN sitters s ON s.id = r.sitter_id
		 WHERE r.id = $1`, roomID).Scan(&sitterID, &sitterUserID, &title); err != nil {
		NotFound(c, "直播不存在")
		return
	}

	tag, err := db.Pool.Exec(context.Background(),
		`UPDATE live_rooms SET status = 'ended', ended_at = now(), end_reason = 'admin_stop', updated_at = now()
		 WHERE id = $1 AND status = 'living'`, roomID)
	if err != nil {
		InternalError(c, "强制下播失败")
		return
	}
	if tag.RowsAffected() == 0 {
		Conflict(c, "直播已结束")
		return
	}
	_, _ = db.Pool.Exec(context.Background(), "DELETE FROM live_viewers WHERE room_id = $1", roomID)

	detail := fmt.Sprintf(`{"room_id": %d}`, roomID)
	if reason != "" {
		detail = fmt.Sprintf(`{"room_id": %d, "reason": %q}`, roomID, reason)
	}
	writeAudit(c, "LIVE_ADMIN_STOP", "live_room", roomID, detail)
	_, _ = db.Pool.Exec(context.Background(),
		"SELECT fn_notify($1, 'system_announcement', $2, $3, $4, $5)",
		sitterUserID, "直播被平台下播",
		"《"+title+"》已被平台下播"+reasonSuffix(reason),
		fmt.Sprintf(`{"room_id": %d}`, roomID), "business")

	OK(c, gin.H{"id": roomID, "status": "ended"})
}

func reasonSuffix(reason string) string {
	if reason == "" {
		return ""
	}
	return "，原因：" + reason
}

// AdminBanLive PUT /api/v1/admin/live/:id/ban
// 封禁该直播对应的机构：终止当前直播 + 禁止再次开播。
func AdminBanLive(c *gin.Context) {
	roomID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		BadRequest(c, "无效的直播ID")
		return
	}
	var in struct {
		Reason string `json:"reason" binding:"required"`
	}
	if err := c.ShouldBindJSON(&in); err != nil {
		BadRequest(c, "请填写封禁原因")
		return
	}
	reason := strings.TrimSpace(in.Reason)
	if reason == "" {
		BadRequest(c, "请填写封禁原因")
		return
	}

	var sitterID, sitterUserID int64
	var title string
	if err := db.Pool.QueryRow(context.Background(),
		`SELECT r.sitter_id, s.user_id, r.title FROM live_rooms r JOIN sitters s ON s.id = r.sitter_id
		 WHERE r.id = $1`, roomID).Scan(&sitterID, &sitterUserID, &title); err != nil {
		NotFound(c, "直播不存在")
		return
	}

	tx, err := db.Pool.Begin(context.Background())
	if err != nil {
		InternalError(c, "事务错误")
		return
	}
	defer tx.Rollback(context.Background())

	if _, err := tx.Exec(context.Background(),
		`UPDATE sitters SET live_banned_at = now(), live_ban_reason = $1 WHERE id = $2`, reason, sitterID); err != nil {
		InternalError(c, "封禁失败")
		return
	}
	if _, err := tx.Exec(context.Background(),
		`UPDATE live_rooms SET status = 'banned', ended_at = now(), end_reason = 'banned',
		        ban_reason = $1, updated_at = now()
		 WHERE sitter_id = $2 AND status = 'living'`, reason, sitterID); err != nil {
		InternalError(c, "结束直播失败")
		return
	}
	_, _ = tx.Exec(context.Background(),
		"DELETE FROM live_viewers WHERE room_id IN (SELECT id FROM live_rooms WHERE sitter_id = $1)", sitterID)
	_, _ = tx.Exec(context.Background(),
		"SELECT fn_notify($1, 'system_announcement', $2, $3, $4, $5)",
		sitterUserID, "直播权限已被封禁",
		fmt.Sprintf("你的直播《%s》已被平台封禁并终止，原因：%s", title, reason),
		fmt.Sprintf(`{"room_id": %d}`, roomID), "business")
	if err := tx.Commit(context.Background()); err != nil {
		InternalError(c, "提交失败")
		return
	}

	writeAudit(c, "LIVE_BAN_SITTER", "sitter", sitterID,
		fmt.Sprintf(`{"room_id": %d, "reason": %q}`, roomID, reason))
	OK(c, gin.H{"room_id": roomID, "sitter_id": sitterID, "sitter_banned": true})
}

// AdminSetSitterLiveBan PUT /api/v1/admin/sitters/:id/live-ban
func AdminSetSitterLiveBan(c *gin.Context) {
	sitterID, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		BadRequest(c, "无效的机构ID")
		return
	}
	var in struct {
		Banned bool   `json:"banned"`
		Reason string `json:"reason"`
	}
	if err := c.ShouldBindJSON(&in); err != nil {
		BadRequest(c, "参数无效")
		return
	}
	reason := strings.TrimSpace(in.Reason)

	var sitterUserID int64
	if err := db.Pool.QueryRow(context.Background(),
		"SELECT user_id FROM sitters WHERE id = $1 AND deleted_at IS NULL", sitterID).Scan(&sitterUserID); err != nil {
		NotFound(c, "机构不存在")
		return
	}

	if !in.Banned {
		if _, err := db.Pool.Exec(context.Background(),
			`UPDATE sitters SET live_banned_at = NULL, live_ban_reason = NULL WHERE id = $1`, sitterID); err != nil {
			InternalError(c, "解除封禁失败")
			return
		}
		writeAudit(c, "LIVE_UNBAN_SITTER", "sitter", sitterID, `{}`)
		OK(c, gin.H{"sitter_id": sitterID, "sitter_banned": false})
		return
	}

	if reason == "" {
		BadRequest(c, "请填写封禁原因")
		return
	}
	tx, err := db.Pool.Begin(context.Background())
	if err != nil {
		InternalError(c, "事务错误")
		return
	}
	defer tx.Rollback(context.Background())
	if _, err := tx.Exec(context.Background(),
		`UPDATE sitters SET live_banned_at = now(), live_ban_reason = $1 WHERE id = $2`, reason, sitterID); err != nil {
		InternalError(c, "封禁失败")
		return
	}
	if _, err := tx.Exec(context.Background(),
		`UPDATE live_rooms SET status = 'banned', ended_at = now(), end_reason = 'banned',
		        ban_reason = $1, updated_at = now()
		 WHERE sitter_id = $2 AND status = 'living'`, reason, sitterID); err != nil {
		InternalError(c, "结束直播失败")
		return
	}
	_, _ = tx.Exec(context.Background(),
		"SELECT fn_notify($1, 'system_announcement', $2, $3, $4, $5)",
		sitterUserID, "直播权限已被封禁",
		"你的直播权限已被平台封禁，原因："+reason,
		`{}`, "business")
	if err := tx.Commit(context.Background()); err != nil {
		InternalError(c, "提交失败")
		return
	}
	writeAudit(c, "LIVE_BAN_SITTER", "sitter", sitterID, fmt.Sprintf(`{"reason": %q}`, reason))
	OK(c, gin.H{"sitter_id": sitterID, "sitter_banned": true})
}

// AdminHideLiveMessage PUT /api/v1/admin/live/messages/:mid/hide
func AdminHideLiveMessage(c *gin.Context) {
	msgID, err := strconv.ParseInt(c.Param("mid"), 10, 64)
	if err != nil {
		BadRequest(c, "无效的弹幕ID")
		return
	}
	var in struct {
		Hidden bool `json:"hidden"`
	}
	_ = c.ShouldBindJSON(&in)
	status := "visible"
	if in.Hidden {
		status = "hidden"
	}
	tag, err := db.Pool.Exec(context.Background(),
		"UPDATE live_messages SET status = $1 WHERE id = $2", status, msgID)
	if err != nil {
		InternalError(c, "处理弹幕失败")
		return
	}
	if tag.RowsAffected() == 0 {
		NotFound(c, "弹幕不存在")
		return
	}
	writeAudit(c, "LIVE_MESSAGE_MODERATE", "live_message", msgID,
		fmt.Sprintf(`{"status": %q}`, status))
	OK(c, gin.H{"id": msgID, "status": status})
}
