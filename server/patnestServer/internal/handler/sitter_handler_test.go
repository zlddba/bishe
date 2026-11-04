package handler

import (
	"reflect"
	"strings"
	"testing"
)

// UpdateMyShop 用 "col = $N, " 拼接 SET 子句，changedColumns 依赖这个格式反推变更列名；
// 两处一旦走偏，审计日志会静默记错内容，所以这里把格式钉住。
func TestChangedColumns(t *testing.T) {
	cases := []struct {
		name   string
		clause string
		want   []string
	}{
		{"单字段", "name = $1, ", []string{"name"}},
		{"多字段", "name = $1, daily_price = $2, capacity = $3, ",
			[]string{"name", "daily_price", "capacity"}},
		{"无后缀", "address = $1", []string{"address"}},
		{"空串", "", nil},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			got := changedColumns(tc.clause)
			if len(tc.want) == 0 {
				if len(got) != 0 {
					t.Fatalf("changedColumns(%q) = %v, want empty", tc.clause, got)
				}
				return
			}
			if !reflect.DeepEqual(got, tc.want) {
				t.Errorf("changedColumns(%q) = %v, want %v", tc.clause, got, tc.want)
			}
		})
	}
}

func TestQuotedNames(t *testing.T) {
	got := quotedNames([]string{"name", "capacity"})
	encoded := "[" + strings.Join(got, ",") + "]"
	want := `["name","capacity"]`
	if encoded != want {
		t.Errorf("quotedNames 序列化 = %s, want %s", encoded, want)
	}
}
