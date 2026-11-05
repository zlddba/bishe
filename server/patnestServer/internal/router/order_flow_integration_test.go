//go:build integration

// 订单主流程回归（连真实数据库，需显式开启）：
//
//	go test -tags integration ./internal/router -run TestOrderFlow -v
//
// 覆盖 2026-11-04 清单里的 B-6（sitter 账号查不到自己下的单）、B-6 连带（改期/续费/取消）
// 与 B-7（列表 owner_id 恒为 0），并对 owner 账号跑同一套流程，确认没有修好 sitter 却弄坏 owner。
// 测试数据在结束时按用户维度硬删除，不会在演示库里留垃圾单。
package router_test

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"math"
	"net/http"
	"net/http/httptest"
	"net/url"
	"testing"
	"time"

	"patnestServer/internal/config"
	"patnestServer/internal/db"
	"patnestServer/internal/middleware"
	"patnestServer/internal/router"
)

type envelope struct {
	Code    int             `json:"code"`
	Message string          `json:"message"`
	Data    json.RawMessage `json:"data"`
}

type apiClient struct {
	t    *testing.T
	base string
	tok  string
}

func (c *apiClient) do(method, path string, body any) (int, envelope) {
	c.t.Helper()
	var rdr io.Reader
	if body != nil {
		raw, err := json.Marshal(body)
		if err != nil {
			c.t.Fatalf("marshal body: %v", err)
		}
		rdr = bytes.NewReader(raw)
	}
	req, err := http.NewRequest(method, c.base+path, rdr)
	if err != nil {
		c.t.Fatalf("new request: %v", err)
	}
	req.Header.Set("Content-Type", "application/json")
	if c.tok != "" {
		req.Header.Set("Authorization", "Bearer "+c.tok)
	}
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		c.t.Fatalf("%s %s: %v", method, path, err)
	}
	defer resp.Body.Close()
	raw, _ := io.ReadAll(resp.Body)
	var env envelope
	_ = json.Unmarshal(raw, &env)
	c.t.Logf("%s %s -> %d %s", method, path, resp.StatusCode, string(raw))
	return resp.StatusCode, env
}

func decode[T any](t *testing.T, env envelope) T {
	t.Helper()
	var v T
	if err := json.Unmarshal(env.Data, &v); err != nil {
		t.Fatalf("decode data %s: %v", string(env.Data), err)
	}
	return v
}

// sameMoney 按分比较金额，避免浮点表示差异带来的断言噪音。
func sameMoney(a, b float64) bool { return math.Abs(a-b) < 0.005 }

// setupServer 连库 + 初始化 JWT + 起一个进程内 HTTP 服务；调用方用完由 Cleanup 收尾。
func setupServer(t *testing.T) string {
	t.Helper()
	cfg := config.Load()
	if err := db.Init(cfg.DBDSN()); err != nil {
		t.Fatalf("连接数据库失败: %v", err)
	}
	// 先注册关闭连接，LIFO 保证它最后执行，清理 SQL 跑完才断开
	t.Cleanup(db.Close)
	middleware.InitJWT(cfg.JWTSecret, 30*time.Minute)

	srv := httptest.NewServer(router.Setup())
	t.Cleanup(srv.Close)
	return srv.URL
}

// registerProbe 注册一个探针账号并返回带令牌的客户端与 user_id。
func registerProbe(t *testing.T, baseURL, role string) (*apiClient, int64, string) {
	t.Helper()
	cl := &apiClient{t: t, base: baseURL}
	username := fmt.Sprintf("probe_%s_%d", role, time.Now().UnixNano())
	phone := probePhone()
	code, env := cl.do("POST", "/api/v1/auth/register", map[string]any{
		"username": username, "password": "Abc12345!", "role": role, "phone": phone,
	})
	if code != http.StatusOK {
		t.Fatalf("注册失败: %d %s", code, env.Message)
	}
	auth := decode[struct {
		AccessToken string `json:"access_token"`
	}](t, env)
	cl.tok = auth.AccessToken

	var userID int64
	if err := db.Pool.QueryRow(context.Background(),
		"SELECT id FROM users WHERE username = $1", username).Scan(&userID); err != nil || userID == 0 {
		t.Fatalf("未能取得注册账号的 user_id: %v", err)
	}
	return cl, userID, username
}

// purgeProbeUser 清除探针账号的订单/宠物/钱包/账号，避免污染演示库。
func purgeProbeUser(t *testing.T, userID int64) {
	t.Helper()
	ctx := context.Background()
	for _, sql := range []string{
		`DELETE FROM wallet_transactions WHERE user_id = $1`,
		`DELETE FROM recharge_orders WHERE user_id = $1`,
		`DELETE FROM wallets WHERE user_id = $1`,
		`DELETE FROM payments WHERE order_id IN (SELECT id FROM orders WHERE owner_id = $1)`,
		`DELETE FROM order_pet WHERE order_id IN (SELECT id FROM orders WHERE owner_id = $1)`,
		`DELETE FROM orders WHERE owner_id = $1`,
		`DELETE FROM pets WHERE owner_id = $1`,
		`DELETE FROM users WHERE id = $1`,
	} {
		if _, err := db.Pool.Exec(ctx, sql, userID); err != nil {
			t.Logf("[cleanup] %v", err)
		}
	}
}

// TestOrderFlow 对 owner / sitter 两种角色各跑一遍完整主流程。
func TestOrderFlow(t *testing.T) {
	baseURL := setupServer(t)
	for _, role := range []string{"owner", "sitter"} {
		role := role
		t.Run(role, func(t *testing.T) {
			runOrderFlow(t, baseURL, role)
		})
	}
}

func runOrderFlow(t *testing.T, baseURL, role string) {
	cl := &apiClient{t: t, base: baseURL}
	username := fmt.Sprintf("probe_%s_%d", role, time.Now().UnixNano())
	phone := probePhone()

	// 0. 注册必须填手机号：缺手机号要被拦住（而不是建出一个没手机号的账号）
	code, env := cl.do("POST", "/api/v1/auth/register", map[string]any{
		"username": username + "_nophone", "password": "Abc12345!", "role": role,
	})
	if code != http.StatusBadRequest {
		t.Fatalf("未填手机号的注册应返回 400，实际 %d %s", code, env.Message)
	}

	// 1. 注册（role=sitter 是本轮 Bug 的触发条件）
	code, env = cl.do("POST", "/api/v1/auth/register", map[string]any{
		"username": username, "password": "Abc12345!", "role": role, "phone": phone,
	})
	if code != http.StatusOK {
		t.Fatalf("注册失败: %d %s", code, env.Message)
	}
	auth := decode[struct {
		User struct {
			Username   string `json:"username"`
			Phone      string `json:"phone"`
			PhoneBound bool   `json:"phone_bound"`
		} `json:"user"`
		AccessToken   string `json:"access_token"`
		NeedBindPhone bool   `json:"need_bind_phone"`
	}](t, env)
	if auth.AccessToken == "" {
		t.Fatal("注册未返回 access_token")
	}
	if auth.User.Phone != phone || !auth.User.PhoneBound || auth.NeedBindPhone {
		t.Fatalf("注册响应手机号状态不对: %+v", auth)
	}
	cl.tok = auth.AccessToken

	// 1.1 手机号也能登录（与用户名同一个输入框），并回带 phone_bound
	byPhone := &apiClient{t: t, base: baseURL}
	code, env = byPhone.do("POST", "/api/v1/auth/login", map[string]any{
		"username": phone, "password": "Abc12345!", "client": "app",
	})
	if code != http.StatusOK {
		t.Fatalf("用手机号登录失败: %d %s", code, env.Message)
	}
	phoneLogin := decode[struct {
		User struct {
			Username   string `json:"username"`
			PhoneBound bool   `json:"phone_bound"`
		} `json:"user"`
		NeedBindPhone bool `json:"need_bind_phone"`
	}](t, env)
	if phoneLogin.User.Username != username || phoneLogin.NeedBindPhone {
		t.Fatalf("手机号登录返回的账号不对: %+v", phoneLogin)
	}

	// 直接按用户名取 user id，用于断言与收尾清理
	var userID int64
	if err := db.Pool.QueryRow(context.Background(),
		"SELECT id FROM users WHERE username = $1", username).Scan(&userID); err != nil || userID == 0 {
		t.Fatalf("未能取得注册账号的 user_id: %v", err)
	}
	t.Cleanup(func() { purgeProbeUser(t, userID) })

	// 2. 建档
	code, env = cl.do("POST", "/api/v1/pet-owner/pets", map[string]any{"name": "探针", "species": "dog"})
	if code != http.StatusOK {
		t.Fatalf("建宠物失败: %d %s", code, env.Message)
	}
	pet := decode[struct {
		ID int64 `json:"id"`
	}](t, env)

	// 3. 下单（用可正常定价的机构 6）
	code, env = cl.do("POST", "/api/v1/pet-owner/orders", map[string]any{
		"sitter_id": 6, "start_date": "2027-06-01", "end_date": "2027-06-03",
		"pets": []map[string]any{{"pet_id": pet.ID}},
	})
	if code != http.StatusOK {
		t.Fatalf("下单失败: %d %s", code, env.Message)
	}
	order := decode[struct {
		ID int64 `json:"order_id"`
	}](t, env)
	if order.ID == 0 {
		t.Fatalf("下单未返回 order_id: %s", string(env.Data))
	}
	orderPath := fmt.Sprintf("/api/v1/pet-owner/orders/%d", order.ID)

	// 4. 列表：items 非空且 owner_id 不能是 0（B-7）
	code, env = cl.do("GET", "/api/v1/pet-owner/orders?per_page=10", nil)
	if code != http.StatusOK {
		t.Fatalf("列表失败: %d %s", code, env.Message)
	}
	list := decode[struct {
		Items []struct {
			ID       int64 `json:"id"`
			OwnerID  int64 `json:"owner_id"`
			SitterID int64 `json:"sitter_id"`
		} `json:"items"`
		Total int `json:"total"`
	}](t, env)
	if len(list.Items) == 0 {
		t.Fatal("列表 items 为空（B-1 回归）")
	}
	for _, it := range list.Items {
		if it.OwnerID == 0 {
			t.Fatalf("列表 owner_id 恒为 0（B-7 未修）: %+v", it)
		}
		if it.OwnerID != userID {
			t.Fatalf("列表 owner_id=%d，期望 %d", it.OwnerID, userID)
		}
	}

	// 5. 详情：自己下的单必须能查到（B-6 核心，owner/sitter 都要 200）
	code, env = cl.do("GET", orderPath, nil)
	if code != http.StatusOK {
		t.Fatalf("详情 %s 返回 %d %s（B-6 未修）", orderPath, code, env.Message)
	}

	// 6. 支付
	code, env = cl.do("PUT", orderPath+"/pay", map[string]any{"method": "mock"})
	if code != http.StatusOK {
		t.Fatalf("支付失败: %d %s", code, env.Message)
	}

	// 7. 改期（等长换档，不产生差额）
	code, env = cl.do("PUT", orderPath+"/reschedule", map[string]any{
		"start_date": "2027-06-08", "end_date": "2027-06-10",
	})
	if code != http.StatusOK {
		t.Fatalf("改期失败: %d %s（B-6 连带）", code, env.Message)
	}

	// 8. 续费
	code, env = cl.do("PUT", orderPath+"/renew", map[string]any{"end_date": "2027-06-12"})
	if code != http.StatusOK {
		t.Fatalf("续费失败: %d %s（B-6 连带）", code, env.Message)
	}

	// 9. 取消（触发释放容量）
	code, env = cl.do("PUT", orderPath+"/cancel", map[string]any{"reason": "集成测试收尾"})
	if code != http.StatusOK {
		t.Fatalf("取消失败: %d %s（B-6 连带）", code, env.Message)
	}

	// 10. 机构视角列表参数可用（P2）
	code, env = cl.do("GET", "/api/v1/pet-owner/orders?scope=received", nil)
	if code != http.StatusOK {
		t.Fatalf("scope=received 失败: %d %s", code, env.Message)
	}
	code, env = cl.do("GET", "/api/v1/pet-owner/orders?scope=bogus", nil)
	if code != http.StatusBadRequest {
		t.Fatalf("非法 scope 应返回 400，实际 %d %s", code, env.Message)
	}

	// 11. 历史账号没有手机号：登录仍然成功，但 need_bind_phone=true，客户端据此弹「请绑定手机号」
	if _, err := db.Pool.Exec(context.Background(),
		"UPDATE users SET phone = NULL WHERE id = $1", userID); err != nil {
		t.Fatalf("清空手机号失败: %v", err)
	}
	noPhone := &apiClient{t: t, base: baseURL}
	code, env = noPhone.do("POST", "/api/v1/auth/login", map[string]any{
		"username": username, "password": "Abc12345!", "client": "app",
	})
	if code != http.StatusOK {
		t.Fatalf("老账号（无手机号）应能登录，实际 %d %s", code, env.Message)
	}
	np := decode[struct {
		User struct {
			Phone      string `json:"phone"`
			PhoneBound bool   `json:"phone_bound"`
		} `json:"user"`
		NeedBindPhone bool `json:"need_bind_phone"`
	}](t, env)
	if !np.NeedBindPhone || np.User.PhoneBound || np.User.Phone != "" {
		t.Fatalf("无手机号账号登录应提示绑定，实际 %+v", np)
	}
}

// probePhone 生成符合 ^1[3-9]\d{9}$ 的测试手机号，避免与库中已有号码撞唯一约束。
func probePhone() string {
	return fmt.Sprintf("199%08d", time.Now().UnixNano()%100_000_000)
}

// TestWalletBalancePay 覆盖 N-1：充值 → 余额支付 → 流水 → 重复支付不重复扣款。
func TestWalletBalancePay(t *testing.T) {
	baseURL := setupServer(t)
	cl, userID, _ := registerProbe(t, baseURL, "owner")
	t.Cleanup(func() { purgeProbeUser(t, userID) })

	// 1. 充值 500 并确认到账
	code, env := cl.do("POST", "/api/v1/pet-owner/wallet/recharge", map[string]any{
		"amount": 500, "channel": "mock",
	})
	if code != http.StatusOK {
		t.Fatalf("发起充值失败: %d %s", code, env.Message)
	}
	ro := decode[struct {
		ID int64 `json:"recharge_id"`
	}](t, env)
	code, env = cl.do("POST", fmt.Sprintf("/api/v1/pet-owner/wallet/recharge/%d/pay", ro.ID), map[string]any{})
	if code != http.StatusOK {
		t.Fatalf("确认充值失败: %d %s", code, env.Message)
	}
	if got := decode[struct {
		Balance float64 `json:"balance"`
	}](t, env); !sameMoney(got.Balance, 500) {
		t.Fatalf("充值后余额 = %v，期望 500", got.Balance)
	}

	// 2. 建宠物 + 下单（机构 6 日单价 80，2 天 = 160）
	code, env = cl.do("POST", "/api/v1/pet-owner/pets", map[string]any{"name": "钱包探针", "species": "dog"})
	if code != http.StatusOK {
		t.Fatalf("建宠物失败: %d %s", code, env.Message)
	}
	pet := decode[struct {
		ID int64 `json:"id"`
	}](t, env)
	code, env = cl.do("POST", "/api/v1/pet-owner/orders", map[string]any{
		"sitter_id": 6, "start_date": "2027-08-01", "end_date": "2027-08-03",
		"pets": []map[string]any{{"pet_id": pet.ID}},
	})
	if code != http.StatusOK {
		t.Fatalf("下单失败: %d %s", code, env.Message)
	}
	order := decode[struct {
		OrderID int64   `json:"order_id"`
		Amount  float64 `json:"total_amount"`
	}](t, env)
	if !sameMoney(order.Amount, 160) {
		t.Fatalf("订单金额 = %v，期望 160（机构 6 的价格可能被改过）", order.Amount)
	}
	orderPath := fmt.Sprintf("/api/v1/pet-owner/orders/%d", order.OrderID)

	// 3. 余额支付：这是 N-1 的核心，必须 200
	code, env = cl.do("PUT", orderPath+"/pay", map[string]any{"method": "balance"})
	if code != http.StatusOK {
		t.Fatalf("余额支付失败: %d %s", code, env.Message)
	}

	// 4. 余额扣减 160，流水多一条 consume（channel=balance，ref_type=order）
	code, env = cl.do("GET", "/api/v1/pet-owner/wallet", nil)
	if code != http.StatusOK {
		t.Fatalf("查钱包失败: %d %s", code, env.Message)
	}
	wallet := decode[struct {
		Balance      float64 `json:"balance"`
		TotalConsume float64 `json:"total_consume"`
	}](t, env)
	if !sameMoney(wallet.Balance, 340) || !sameMoney(wallet.TotalConsume, 160) {
		t.Fatalf("余额/累计消费 = %v/%v，期望 340/160", wallet.Balance, wallet.TotalConsume)
	}

	code, env = cl.do("GET", "/api/v1/pet-owner/wallet/transactions?type=consume", nil)
	if code != http.StatusOK {
		t.Fatalf("查流水失败: %d %s", code, env.Message)
	}
	txs := decode[struct {
		Items []struct {
			Type         string  `json:"type"`
			Amount       float64 `json:"amount"`
			BalanceAfter float64 `json:"balance_after"`
			Channel      string  `json:"channel"`
			RefType      string  `json:"ref_type"`
			RefID        int64   `json:"ref_id"`
		} `json:"items"`
		Total int `json:"total"`
	}](t, env)
	if txs.Total != 1 || len(txs.Items) != 1 {
		t.Fatalf("consume 流水条数 = %d（items=%d），期望 1 条", txs.Total, len(txs.Items))
	}
	tx := txs.Items[0]
	if tx.Channel != "balance" || tx.RefType != "order" || tx.RefID != order.OrderID ||
		!sameMoney(tx.Amount, 160) || !sameMoney(tx.BalanceAfter, 340) {
		t.Fatalf("consume 流水字段不对: %+v", tx)
	}

	// 5. 重复支付：409，且不能重复扣款（N-1 第 7 条）
	code, env = cl.do("PUT", orderPath+"/pay", map[string]any{"method": "balance"})
	if code != http.StatusConflict {
		t.Fatalf("重复余额支付应返回 409，实际 %d %s", code, env.Message)
	}
	code, env = cl.do("GET", "/api/v1/pet-owner/wallet", nil)
	if code != http.StatusOK {
		t.Fatalf("查钱包失败: %d %s", code, env.Message)
	}
	if got := decode[struct {
		Balance float64 `json:"balance"`
	}](t, env); !sameMoney(got.Balance, 340) {
		t.Fatalf("重复支付后余额 = %v，期望仍是 340（不能扣两次）", got.Balance)
	}
}

// TestSitterPublicContact 覆盖 N-2：机构详情/列表/搜索都要下发联系电话，供「一键拨号」使用。
func TestSitterPublicContact(t *testing.T) {
	baseURL := setupServer(t)
	ctx := context.Background()

	var ownerUserID int64
	if err := db.Pool.QueryRow(ctx,
		`SELECT user_id FROM sitters WHERE id = 6 AND deleted_at IS NULL`).Scan(&ownerUserID); err != nil {
		t.Fatalf("机构 6 不存在: %v", err)
	}
	var oldPhone, oldName, oldPerson *string
	_ = db.Pool.QueryRow(ctx, `SELECT contact_phone, contact_name FROM users WHERE id = $1`, ownerUserID).
		Scan(&oldPhone, &oldName)
	_ = db.Pool.QueryRow(ctx, `SELECT contact_person FROM sitters WHERE id = 6`).Scan(&oldPerson)

	const testPhone = "13700001234"
	t.Cleanup(func() {
		_, _ = db.Pool.Exec(ctx,
			`UPDATE users SET contact_phone = $1, contact_name = $2 WHERE id = $3`, oldPhone, oldName, ownerUserID)
		_, _ = db.Pool.Exec(ctx, `UPDATE sitters SET contact_person = $1 WHERE id = 6`, oldPerson)
	})
	if _, err := db.Pool.Exec(ctx,
		`UPDATE users SET contact_phone = $1, contact_name = $2 WHERE id = $3`, testPhone, "测试联系人", ownerUserID); err != nil {
		t.Fatalf("准备联系方式失败: %v", err)
	}
	if _, err := db.Pool.Exec(ctx, `UPDATE sitters SET contact_person = $1 WHERE id = 6`, "张三"); err != nil {
		t.Fatalf("准备联系人失败: %v", err)
	}

	cl, userID, _ := registerProbe(t, baseURL, "owner")
	t.Cleanup(func() { purgeProbeUser(t, userID) })

	type contactFields struct {
		ContactPhone  string `json:"contact_phone"`
		ContactName   string `json:"contact_name"`
		ContactPerson string `json:"contact_person"`
	}

	// 详情
	code, env := cl.do("GET", "/api/v1/pet-owner/sitters/6", nil)
	if code != http.StatusOK {
		t.Fatalf("机构详情失败: %d %s", code, env.Message)
	}
	detail := decode[contactFields](t, env)
	if detail.ContactPhone != testPhone || detail.ContactName != "测试联系人" || detail.ContactPerson != "张三" {
		t.Fatalf("详情缺少联系方式: %+v", detail)
	}

	// 列表
	code, env = cl.do("GET", "/api/v1/pet-owner/sitters?per_page=50", nil)
	if code != http.StatusOK {
		t.Fatalf("机构列表失败: %d %s", code, env.Message)
	}
	list := decode[struct {
		Items []struct {
			ID int64 `json:"id"`
			contactFields
		} `json:"items"`
	}](t, env)
	found := false
	for _, it := range list.Items {
		if it.ID == 6 {
			found = true
			if it.ContactPhone != testPhone {
				t.Fatalf("列表里机构 6 的 contact_phone = %q，期望 %q", it.ContactPhone, testPhone)
			}
		}
	}
	if !found {
		t.Fatal("机构列表里没有 id=6")
	}

	// 搜索
	code, env = cl.do("GET", "/api/v1/pet-owner/sitters/search?q="+url.QueryEscape("小满"), nil)
	if code != http.StatusOK {
		t.Fatalf("机构搜索失败: %d %s", code, env.Message)
	}
	search := decode[struct {
		Items []contactFields `json:"items"`
	}](t, env)
	if len(search.Items) == 0 {
		t.Fatal("搜索没有结果")
	}
	if search.Items[0].ContactPhone != testPhone {
		t.Fatalf("搜索结果 contact_phone = %q，期望 %q", search.Items[0].ContactPhone, testPhone)
	}
}
