package handler

import "testing"

// riffHead 拼出一个 12 字节的 RIFF 容器头，尾部 4 字节是真正的格式标识。
func riffHead(format string) []byte {
	head := append([]byte("RIFF"), []byte{0, 0, 0, 0}...)
	return append(head, []byte(format)...)
}

func TestSniffImageType(t *testing.T) {
	cases := []struct {
		name string
		head []byte
		ext  string
		mime string
		ok   bool
	}{
		{"png", []byte{0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0}, ".png", "image/png", true},
		{"jpeg", []byte{0xff, 0xd8, 0xff, 0xe0, 0, 0, 0, 0, 0, 0, 0, 0}, ".jpg", "image/jpeg", true},
		{"gif89a", append([]byte("GIF89a"), make([]byte, 6)...), ".gif", "image/gif", true},
		{"gif87a", append([]byte("GIF87a"), make([]byte, 6)...), ".gif", "image/gif", true},
		{"webp", riffHead("WEBP"), ".webp", "image/webp", true},
		{"php-shelled-jpg", append([]byte("<?php system($_GET[0]);"), make([]byte, 32)...), "", "", false},
		{"short", []byte{0x89, 'P', 'N', 'G'}, "", "", false},
		{"empty", nil, "", "", false},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			mime, ext, ok := sniffImageType(tc.head)
			if ok != tc.ok {
				t.Fatalf("ok = %v, want %v (mime=%q ext=%q)", ok, tc.ok, mime, ext)
			}
			if !tc.ok {
				return
			}
			if mime != tc.mime || ext != tc.ext {
				t.Errorf("got %s/%s, want %s/%s", mime, ext, tc.mime, tc.ext)
			}
		})
	}
}

// webp 的容器头是 RIFF，后 4 字节才是 WEBP；这两个用例锁住"只看前 4 字节就够了"的偷懒实现。
func TestSniffImageTypeRejectsNonWebpRiff(t *testing.T) {
	head := riffHead("AVI ")
	if _, _, ok := sniffImageType(head); ok {
		t.Fatal("RIFF/AVI 不应被当作 webp")
	}
}
