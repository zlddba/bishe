package handler

import (
	"math"
	"testing"
)

// sameMoney 按分比较，避免 roundMoney 结果的浮点表示差异写成断言噪音。
func sameMoney(got, want float64) bool {
	return math.Abs(got-want) < 0.005
}

// 续住原来的写法把 total_amount 直接覆盖成整段住宿基础价：加购、优惠券、积分抵扣全部被抹掉，
// 差价也没真的收进支付流水。renewAmounts 把口径钉成"原价 + 新增服务费"，
// 后台退款的金额上限（refundAmount <= total_amount）依赖这个不变式成立。
func TestRenewAmounts(t *testing.T) {
	cases := []struct {
		name       string
		estimated  float64
		total      float64
		added      float64
		wantEst    float64
		wantTotal  float64
		wantCharge float64
	}{
		{"按天续住三晚", 300, 320, 90, 390, 410, 90},
		{"原价含加购与优惠时不被覆盖", 1000, 875, 200, 1200, 1075, 200},
		{"零差价不产生流水", 300, 300, 0, 300, 300, 0},
		{"负数增量按零收费", 300, 300, -50, 300, 300, 0},
		{"逐日累加的浮点尾差归整到分", 100, 100, 66.666, 166.67, 166.67, 66.67},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			est, total, charge := renewAmounts(tc.estimated, tc.total, tc.added)
			if near(est, tc.wantEst) || near(total, tc.wantTotal) || near(charge, tc.wantCharge) {
				t.Fatalf("renewAmounts(%v, %v, %v) = (%v, %v, %v), want (%v, %v, %v)",
					tc.estimated, tc.total, tc.added, est, total, charge,
					tc.wantEst, tc.wantTotal, tc.wantCharge)
			}
			if total < tc.total {
				t.Fatalf("续住后订单金额低于原实付：total=%v old=%v", total, tc.total)
			}
		})
	}
}

// near 判断金额是否偏离期望值超过一分的一半。
func near(got, want float64) bool {
	diff := got - want
	if diff < 0 {
		diff = -diff
	}
	return diff > 0.005
}

// 改期原来也把 total_amount 覆盖成整段住宿基础价，加购与优惠同样会被抹掉，
// 而退款上限是按 total_amount 校验的，账实就会对不上。
func TestRescheduleAmounts(t *testing.T) {
	cases := []struct {
		name      string
		estimated float64
		total     float64
		newStay   float64
		wantEst   float64
		wantTotal float64
		wantDiff  float64
	}{
		{"改成更便宜的档期", 300, 320, 240, 240, 260, -60},
		{"改成更贵的档期", 300, 320, 420, 420, 440, 120},
		{"天数不变金额不变", 300, 320, 300, 300, 320, 0},
		{"加购与优惠不因改期丢失", 1000, 875, 900, 900, 775, -100},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			est, total, diff := rescheduleAmounts(tc.estimated, tc.total, tc.newStay)
			if !sameMoney(est, tc.wantEst) || !sameMoney(total, tc.wantTotal) || !sameMoney(diff, tc.wantDiff) {
				t.Fatalf("rescheduleAmounts(%v, %v, %v) = (%v, %v, %v), want (%v, %v, %v)",
					tc.estimated, tc.total, tc.newStay, est, total, diff,
					tc.wantEst, tc.wantTotal, tc.wantDiff)
			}
			// 订单金额与住宿基础价之差必须等于原单的加购减优惠，改期不该动它
			if !sameMoney(total-est, tc.total-tc.estimated) {
				t.Fatalf("加购/优惠部分被改写：total-estimated=%v, 原为 %v",
					total-est, tc.total-tc.estimated)
			}
		})
	}
}
