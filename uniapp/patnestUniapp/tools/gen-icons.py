#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
宠栖 PatNest · 小程序图标生成器

为什么不用 element-plus icons：
    element-plus 的图标是基于 DOM + SVG sprite 的 Vue 组件，依赖浏览器的
    <svg><use> 机制，微信小程序的渲染层没有这套能力，引入只会白屏。
    小程序里可行的是：位图 PNG、iconfont 字体、或手绘路径。

本脚本用 Pillow 生成一套**线性描边风格**的 PNG 图标，
配色取自项目自有色板（docs/宠栖后台-UI配色设计图.html）：
    主色   #AF4A62 / #C2456A（品牌玫红）
    选中   #AF4A62
    默认   #9AA3AF（tabBar 未选中）

设计约定：
    * 所有图标画在 64x64 坐标系内，视觉重心居中
    * 线宽 3（64 基准），端点圆角
    * 图形只占 12~52 区域，四周留白，缩到 24rpx 时不糊

用法：python tools/gen-icons.py
输出：uniapp/patnestUniapp/static/icons/*.png
"""
import os
import math
from PIL import Image, ImageDraw

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(BASE, 'static', 'icons')

SS = 4          # 超采样倍数（画 4 倍再缩回，边缘更平滑）
SIZE = 64       # 成品尺寸

BRAND = (175, 74, 98)      # #AF4A62 选中/强调
BRAND_DEEP = (194, 69, 106)  # #C2456A
GRAY = (154, 163, 175)      # #9AA3AF 默认
WHITE = (255, 255, 255)

# tabBar 用的四个：home=找寄养（放大镜） order=订单（单据）
#                  message=消息（气泡） mine=我的（人像）
TAB_ICONS = ['home', 'order', 'message', 'mine']

# 功能图标
FEATURE_ICONS = [
    'location', 'navigation', 'pet', 'camera', 'star', 'star-fill',
    'coupon', 'wallet', 'shield', 'edit', 'setting', 'shop', 'lock',
    'plus', 'arrow', 'check', 'close', 'image', 'calendar', 'clock',
    'trash', 'phone', 'message', 'order', 'bell',
    'heart', 'heart-fill', 'search', 'filter', 'fullscreen',
]

# 需要额外产出的「填充/空心」变体（同一个绘制函数，用 fill 参数区分）
FILL_VARIANTS = {'star-fill': 'star', 'heart-fill': 'heart'}

# 这些图标默认用灰色（未选中态），有 filled 变体时用品牌色
GRAY_ICONS = {'heart'}


def canvas():
    img = Image.new('RGBA', (SIZE * SS, SIZE * SS), (0, 0, 0, 0))
    return img, ImageDraw.Draw(img)


def save(img, name):
    img = img.resize((SIZE, SIZE), Image.LANCZOS)
    path = os.path.join(OUT, name + '.png')
    img.save(path)
    return path


def S(v):
    """把 64 基准的坐标放大到超采样画布"""
    return v * SS


def lw(w):
    """线宽（64 基准 → 超采样）"""
    return max(1, int(round(w * SS)))


def poly(d, pts, color, w=3, fill=False):
    """折线（自动闭合到起点）"""
    flat = []
    for p in pts:
        flat.append(S(p[0]))
        flat.append(S(p[1]))
    if fill:
        d.polygon(flat, fill=color)
    else:
        d.line(flat + [flat[0], flat[1]], fill=color, width=lw(w), joint='curve')


# ---------------------------------------------------------------------------
# 各图标绘制函数：均以 64x64 为坐标系，中心 (32,32)
# ---------------------------------------------------------------------------

def icon_home(d, c, w=3):
    """放大镜（找机构）"""
    d.ellipse([S(14), S(14), S(42), S(42)], outline=c, width=lw(w))
    d.line([S(39), S(39), S(51), S(51)], fill=c, width=lw(w + 1))


def icon_order(d, c, w=3):
    """单据（订单）"""
    d.rounded_rectangle([S(15), S(11), S(49), S(53)], radius=S(5), outline=c, width=lw(w))
    for i, y in enumerate((22, 31, 40)):
        x2 = 41 if i < 2 else 35
        d.line([S(23), S(y), S(x2), S(y)], fill=c, width=lw(w))


def icon_message(d, c, w=3):
    """对话气泡（消息）"""
    d.rounded_rectangle([S(13), S(14), S(51), S(42)], radius=S(9), outline=c, width=lw(w))
    d.line([S(22), S(41), S(22), S(52), S(32), S(42)], fill=c, width=lw(w), joint='curve')


def icon_mine(d, c, w=3):
    """人像（我的）"""
    d.ellipse([S(22), S(13), S(42), S(33)], outline=c, width=lw(w))
    d.arc([S(14), S(38), S(50), S(66)], start=180, end=360, fill=c, width=lw(w))


def icon_location(d, c, w=3):
    """定位针"""
    d.ellipse([S(20), S(8), S(44), S(32)], outline=c, width=lw(w))
    d.line([S(25), S(29), S(32), S(54), S(39), S(29)], fill=c, width=lw(w), joint='curve')
    d.ellipse([S(27), S(15), S(37), S(25)], outline=c, width=lw(w - 1))


def icon_navigation(d, c, w=3):
    """指南针"""
    d.ellipse([S(10), S(10), S(54), S(54)], outline=c, width=lw(w))
    d.polygon([S(39), S(25), S(33), S(39), S(25), S(39)], fill=c)
    d.polygon([S(25), S(25), S(31), S(39), S(39), S(39)], outline=c, width=lw(2))


def icon_pet(d, c, w=3):
    """爪印"""
    d.ellipse([S(16), S(30), S(48), S(54)], outline=c, width=lw(w))
    d.ellipse([S(15), S(18), S(25), S(31)], outline=c, width=lw(w - 1))
    d.ellipse([S(27), S(11), S(37), S(24)], outline=c, width=lw(w - 1))
    d.ellipse([S(39), S(18), S(49), S(31)], outline=c, width=lw(w - 1))


def icon_camera(d, c, w=3):
    """相机"""
    d.rounded_rectangle([S(10), S(18), S(54), S(47)], radius=S(6), outline=c, width=lw(w))
    d.line([S(24), S(18), S(28), S(12), S(36), S(12), S(40), S(18)], fill=c, width=lw(w), joint='curve')
    d.ellipse([S(23), S(24), S(41), S(42)], outline=c, width=lw(w - 1))


def _star_points(cx, cy, r_out, r_in, n=5, rot=-90):
    pts = []
    for i in range(n * 2):
        r = r_out if i % 2 == 0 else r_in
        a = math.radians(rot + i * 180.0 / n)
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return pts


def icon_star(d, c, w=3, fill=False):
    """星星"""
    pts = _star_points(32, 33, 21, 9)
    flat = []
    for p in pts:
        flat.append(S(p[0]))
        flat.append(S(p[1]))
    if fill:
        d.polygon(flat, fill=c)
    else:
        d.line(flat + [flat[0], flat[1]], fill=c, width=lw(w), joint='curve')


def icon_heart(d, c, w=3, fill=False):
    """心形（收藏）
    两个大半圆 + 底部浅 V。关键是「圆弧高度 : V 深度」≈ 1 : 1.4，
    V 太深会变成气球/钻石形。"""
    # 左圆弧 bbox [10,12,34,36] → 圆心(22,24) 半径12，上半弧 y 12~24
    arc_l = [S(10), S(12), S(34), S(36)]
    # 右圆弧 bbox [30,12,54,36] → 圆心(42,24) 半径12
    arc_r = [S(30), S(12), S(54), S(36)]
    if fill:
        d.pieslice(arc_l, 180, 360, fill=c)
        d.pieslice(arc_r, 180, 360, fill=c)
        d.polygon([S(10), S(24), S(54), S(24), S(32), S(45)], fill=c)
    else:
        d.arc(arc_l, 180, 360, fill=c, width=lw(w))
        d.arc(arc_r, 180, 360, fill=c, width=lw(w))
        d.line([S(10), S(24), S(32), S(45)], fill=c, width=lw(w), joint='curve')
        d.line([S(54), S(24), S(32), S(45)], fill=c, width=lw(w), joint='curve')


def icon_coupon(d, c, w=3):
    """优惠券（票券）"""
    d.rounded_rectangle([S(11), S(18), S(53), S(46)], radius=S(5), outline=c, width=lw(w))
    d.line([S(32), S(18), S(32), S(46)], fill=c, width=lw(w))


def icon_wallet(d, c, w=3):
    """钱包"""
    d.rounded_rectangle([S(10), S(18), S(54), S(48)], radius=S(6), outline=c, width=lw(w))
    d.line([S(10), S(27), S(54), S(27)], fill=c, width=lw(w))
    d.ellipse([S(40), S(33), S(48), S(41)], outline=c, width=lw(w - 1))


def icon_shield(d, c, w=3):
    """盾牌"""
    d.line([S(32), S(9), S(51), S(17), S(51), S(32)], fill=c, width=lw(w), joint='curve')
    d.line([S(51), S(32), S(32), S(55), S(13), S(32)], fill=c, width=lw(w), joint='curve')
    d.line([S(13), S(32), S(13), S(17), S(32), S(9)], fill=c, width=lw(w), joint='curve')


def icon_edit(d, c, w=3):
    """编辑（铅笔）"""
    d.polygon([S(14), S(50), S(16), S(40), S(40), S(16), S(48), S(24), S(24), S(48)], outline=c, width=lw(w))
    d.line([S(16), S(50), S(24), S(48)], fill=c, width=lw(w))


def icon_setting(d, c, w=3):
    """设置（齿轮）"""
    cx = cy = 32
    d.ellipse([S(cx - 7), S(cy - 7), S(cx + 7), S(cy + 7)], outline=c, width=lw(w))
    for i in range(8):
        a = math.radians(i * 45)
        x1 = cx + 11 * math.cos(a)
        y1 = cy + 11 * math.sin(a)
        x2 = cx + 20 * math.cos(a)
        y2 = cy + 20 * math.sin(a)
        d.line([S(x1), S(y1), S(x2), S(y2)], fill=c, width=lw(w + 1))


def icon_shop(d, c, w=3):
    """店铺（房子）"""
    d.line([S(9), S(29), S(32), S(10), S(55), S(29)], fill=c, width=lw(w), joint='curve')
    d.line([S(15), S(27), S(15), S(54), S(49), S(54), S(49), S(27)], fill=c, width=lw(w), joint='curve')
    d.rounded_rectangle([S(26), S(38), S(38), S(54)], radius=S(2), outline=c, width=lw(w - 1))


def icon_lock(d, c, w=3):
    """锁"""
    d.rounded_rectangle([S(15), S(28), S(49), S(53)], radius=S(5), outline=c, width=lw(w))
    d.arc([S(21), S(11), S(43), S(33)], start=180, end=360, fill=c, width=lw(w))


def icon_plus(d, c, w=3):
    """加号"""
    d.line([S(32), S(16), S(32), S(48)], fill=c, width=lw(w))
    d.line([S(16), S(32), S(48), S(32)], fill=c, width=lw(w))


def icon_arrow(d, c, w=3):
    """右箭头"""
    d.line([S(18), S(32), S(45), S(32)], fill=c, width=lw(w))
    d.line([S(36), S(22), S(46), S(32), S(36), S(42)], fill=c, width=lw(w), joint='curve')


def icon_check(d, c, w=3):
    """对勾"""
    d.line([S(15), S(33), S(27), S(45), S(50), S(19)], fill=c, width=lw(w + 1), joint='curve')


def icon_close(d, c, w=3):
    """叉"""
    d.line([S(19), S(19), S(45), S(45)], fill=c, width=lw(w))
    d.line([S(45), S(19), S(19), S(45)], fill=c, width=lw(w))


def icon_image(d, c, w=3):
    """图片"""
    d.rounded_rectangle([S(10), S(14), S(54), S(50)], radius=S(5), outline=c, width=lw(w))
    d.ellipse([S(20), S(22), S(29), S(31)], outline=c, width=lw(w - 1))
    d.line([S(13), S(44), S(26), S(33), S(36), S(42), S(43), S(36), S(51), S(44)], fill=c, width=lw(w - 1), joint='curve')


def icon_calendar(d, c, w=3):
    """日历"""
    d.rounded_rectangle([S(11), S(15), S(53), S(53)], radius=S(5), outline=c, width=lw(w))
    d.line([S(11), S(26), S(53), S(26)], fill=c, width=lw(w))
    d.line([S(21), S(10), S(21), S(20)], fill=c, width=lw(w))
    d.line([S(43), S(10), S(43), S(20)], fill=c, width=lw(w))


def icon_clock(d, c, w=3):
    """时钟"""
    d.ellipse([S(10), S(10), S(54), S(54)], outline=c, width=lw(w))
    d.line([S(32), S(20), S(32), S(33), S(42), S(39)], fill=c, width=lw(w), joint='curve')


def icon_trash(d, c, w=3):
    """垃圾桶"""
    d.line([S(13), S(18), S(51), S(18)], fill=c, width=lw(w))
    d.line([S(24), S(13), S(40), S(13)], fill=c, width=lw(w))
    d.line([S(18), S(18), S(20), S(53), S(44), S(53), S(46), S(18)], fill=c, width=lw(w), joint='curve')
    d.line([S(28), S(27), S(28), S(44)], fill=c, width=lw(w - 1))
    d.line([S(36), S(27), S(36), S(44)], fill=c, width=lw(w - 1))


def icon_phone(d, c, w=3):
    """电话听筒（经典斜置听筒造型）"""
    d.line([S(20), S(44), S(44), S(20)], fill=c, width=lw(w + 3))
    d.ellipse([S(11), S(38), S(25), S(52)], outline=c, width=lw(w))
    d.ellipse([S(39), S(12), S(53), S(26)], outline=c, width=lw(w))


def icon_bell(d, c, w=3):
    """铃铛"""
    d.arc([S(15), S(12), S(49), S(46)], start=180, end=360, fill=c, width=lw(w))
    d.line([S(15), S(29), S(15), S(44), S(49), S(44), S(49), S(29)], fill=c, width=lw(w), joint='curve')
    d.ellipse([S(28), S(44), S(36), S(52)], outline=c, width=lw(w - 1))
    d.ellipse([S(29), S(7), S(35), S(13)], outline=c, width=lw(w - 1))


def icon_search(d, c, w=3):
    """搜索（放大镜）"""
    d.ellipse([S(13), S(13), S(43), S(43)], outline=c, width=lw(w))
    d.line([S(40), S(40), S(52), S(52)], fill=c, width=lw(w + 1))


def icon_filter(d, c, w=3):
    """筛选（漏斗）"""
    d.line([S(12), S(16), S(52), S(16), S(37), S(34), S(37), S(50), S(27), S(44), S(27), S(34), S(12), S(16)], fill=c, width=lw(w), joint='curve')


def icon_fullscreen(d, c, w=3):
    """全屏（四角向外扩的括号）"""
    # 四个角各画一个「L」形，线宽 3.5 让小尺寸下也看得清
    t = 3.5
    # 左上
    d.line([S(12), S(24), S(12), S(12), S(24), S(12)], fill=c, width=lw(t), joint='curve')
    # 右上
    d.line([S(40), S(12), S(52), S(12), S(52), S(24)], fill=c, width=lw(t), joint='curve')
    # 左下
    d.line([S(12), S(40), S(12), S(52), S(24), S(52)], fill=c, width=lw(t), joint='curve')
    # 右下
    d.line([S(40), S(52), S(52), S(52), S(52), S(40)], fill=c, width=lw(t), joint='curve')


DRAW = {
    'home': icon_home,
    'order': icon_order,
    'message': icon_message,
    'mine': icon_mine,
    'location': icon_location,
    'navigation': icon_navigation,
    'pet': icon_pet,
    'camera': icon_camera,
    'star': icon_star,
    'coupon': icon_coupon,
    'wallet': icon_wallet,
    'shield': icon_shield,
    'edit': icon_edit,
    'setting': icon_setting,
    'shop': icon_shop,
    'lock': icon_lock,
    'plus': icon_plus,
    'arrow': icon_arrow,
    'check': icon_check,
    'close': icon_close,
    'image': icon_image,
    'calendar': icon_calendar,
    'clock': icon_clock,
    'trash': icon_trash,
    'phone': icon_phone,
    'bell': icon_bell,
    'heart': icon_heart,
    'search': icon_search,
    'filter': icon_filter,
    'fullscreen': icon_fullscreen,
}


def render(name, color, fill=False):
    img, d = canvas()
    fn = DRAW[name]
    if fill:
        fn(d, color, w=3, fill=True)
    else:
        fn(d, color, w=3)
    return img


def main():
    os.makedirs(OUT, exist_ok=True)
    made = []
    # 清掉可能残留的预览图，避免被当成图标
    for junk in ('_preview.png', '_check.png'):
        p = os.path.join(OUT, junk)
        if os.path.exists(p):
            os.remove(p)

    # tabBar 图标：默认灰 + 选中玫红
    for name in TAB_ICONS:
        made.append(save(render(name, GRAY), 'tab-' + name))
        made.append(save(render(name, BRAND), 'tab-' + name + '-active'))

    # 功能图标（统一品牌色；GRAY_ICONS 里的默认用灰色）
    for name in FEATURE_ICONS:
        base = FILL_VARIANTS.get(name, name)
        is_fill = name in FILL_VARIANTS
        # 灰色态图标：空心版灰色，filled 版品牌色（对应「未选中 / 已选中」）
        color = BRAND if (is_fill or name not in GRAY_ICONS) else GRAY
        made.append(save(render(base, color, fill=is_fill), name))

    # 店铺的选中态（与 tabBar 一致用品牌色）
    made.append(save(render('shop', BRAND), 'shop-active'))

    # 白色对勾：用于「已勾选」的方块内（玫红底上要看得见）
    made.append(save(render('check', WHITE), 'check-white'))

    # 白色全屏图标：直播间的深色顶栏上用
    made.append(save(render('fullscreen', WHITE), 'fullscreen-white'))

    print('生成 %d 个图标 → %s' % (len(made), OUT))


if __name__ == '__main__':
    main()
