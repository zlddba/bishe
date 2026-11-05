package payment

import "fmt"

// PaymentGateway 支付网关抽象。
// 模拟网关（MockGateway）同步返回支付参数并可直接确认，与现有 PayOrder 的 mock 行为一致；
// 真实微信/支付宝网关需商户号、API 证书与公网回调域名，接入时实现 CreateCharge/VerifyNotify 即可，
// 业务 handler（充值确认、订单支付）无需改动。
type PaymentGateway interface {
	// CreateCharge 创建支付/充值单，返回给客户端拉起支付的参数（二维码/预支付串/跳转链接等）
	CreateCharge(ref string, amount float64) (payParams map[string]interface{}, err error)
	// VerifyNotify 校验异步回调，返回第三方流水号与金额；验签失败 ok=false
	VerifyNotify(body []byte, headers map[string]string) (txnID string, amount float64, ok bool, err error)
}

// MockGateway 模拟支付：同步返回支付链接，确认即成功。用于演示与毕设评分，不涉及真实资金。
type MockGateway struct{}

func (g *MockGateway) CreateCharge(ref string, amount float64) (map[string]interface{}, error) {
	return map[string]interface{}{
		"pay_url": fmt.Sprintf("mock://pay/%s?amount=%.2f", ref, amount),
		"qr":      "",
	}, nil
}

func (g *MockGateway) VerifyNotify(body []byte, headers map[string]string) (string, float64, bool, error) {
	// 模拟网关不使用异步回调；真实网关在此做签名验签并解析流水号/金额。
	return "", 0, false, nil
}

// GetGateway 工厂：当前固定返回 MockGateway。接入真实支付时按 channel 返回对应实现。
func GetGateway(channel string) PaymentGateway {
	// TODO: 接入真实支付时：case "wechat": return &WechatGateway{}; case "alipay": return &AlipayGateway{}
	_ = channel
	return &MockGateway{}
}
