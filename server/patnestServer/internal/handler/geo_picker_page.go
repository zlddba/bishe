package handler

// 桌面端地图选点页：由后端直接渲染，在系统浏览器里打开，用高德 JS API 做交互地图。
// 令牌与 Key 由 html/template 按 JS 上下文转义注入；页面只做"选点 → 回写结果"这一件事。

const geoPickerHTML = `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>宠栖 · 地图选点</title>
<style>
  * { box-sizing: border-box; }
  html, body { height: 100%; margin: 0; }
  body { display: flex; flex-direction: column; background: #F7F4F5; color: #3A2A2F;
         font: 14px/1.5 -apple-system, "Segoe UI", "Microsoft YaHei", sans-serif; }
  header { padding: 14px 18px 10px; background: #fff; border-bottom: 1px solid #EDE4E7; }
  header .ttl { font-size: 17px; font-weight: 600; }
  header .sub { font-size: 12.5px; color: #B2A0A5; margin-top: 2px; }
  .bar { display: flex; gap: 8px; padding: 10px 18px; background: #fff; border-bottom: 1px solid #EDE4E7; }
  .bar input { flex: 1; height: 36px; padding: 0 12px; border: 1px solid #EDE4E7; border-radius: 6px; font-size: 14px; }
  .bar input:focus { outline: none; border-color: #E0708A; }
  button { height: 36px; padding: 0 16px; border: 1px solid #EDE4E7; border-radius: 999px;
           background: #fff; color: #3A2A2F; font-size: 13.5px; cursor: pointer; }
  button.primary { background: #E0708A; border-color: #E0708A; color: #fff; font-weight: 600; }
  button:disabled { opacity: .5; cursor: not-allowed; }
  #tips { display: none; gap: 2px; padding: 6px 18px; background: #fff; border-bottom: 1px solid #EDE4E7;
          max-height: 168px; overflow: auto; }
  #tips.show { display: block; }
  #tips div { padding: 7px 10px; border-radius: 6px; cursor: pointer; }
  #tips div:hover { background: #FBE0E6; }
  #tips .nm { font-weight: 600; }
  #tips .ad { font-size: 12px; color: #B2A0A5; margin-left: 8px; }
  #map { flex: 1; min-height: 260px; }
  footer { display: flex; align-items: center; gap: 12px; padding: 12px 18px; background: #fff;
           border-top: 1px solid #EDE4E7; }
  footer .addr { flex: 1; font-size: 13px; color: #7C6A6F; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .mask { position: fixed; inset: 0; display: flex; align-items: center; justify-content: center;
          background: rgba(247,244,245,.96); text-align: center; padding: 0 24px; font-size: 15px; }
  .mask.hide { display: none; }
  .mask b { display: block; font-size: 17px; margin-bottom: 6px; }
</style>
</head>
<body>
<header>
  <div class="ttl">{{if .PlaceName}}{{.PlaceName}}{{else}}选择机构位置{{end}}</div>
  <div class="sub">拖动地图或点击任意位置放置标记，确认后回到桌面端继续保存</div>
</header>

<div class="bar">
  <input id="kw" placeholder="搜索地点、街道或门店名称" autocomplete="off">
  <button id="btnSearch" class="primary">搜索</button>
</div>
<div id="tips"></div>

<div id="map"></div>

<footer>
  <div class="addr" id="addr">尚未选择位置</div>
  <button id="btnOk" class="primary" disabled>确认选点</button>
</footer>

<div class="mask hide" id="mask">
  <div><b id="maskTtl">这个链接已经用过了</b><span id="maskMsg">请回到桌面端重新点“在地图中选点”</span></div>
</div>

<script>
var TOKEN = {{.Token}};
var JS_KEY = {{.JSKey}};
var JS_SEC = {{.JSSecurity}};
var INIT_LAT = {{.InitialLat}};
var INIT_LNG = {{.InitialLng}};
var ALREADY = {{.AlreadyDone}};
// 没有已存坐标时的默认视野：呼和浩特市中心
var DEFAULT_CENTER = [111.772564, 40.821365];

var AMap, map, marker, geocoder, auto;
var picked = null;
var submitting = false;

var $ = function (id) { return document.getElementById(id); };

function fail(title, msg) {
  $('maskTtl').textContent = title;
  $('maskMsg').textContent = msg;
  $('mask').classList.remove('hide');
}

function loadAmap() {
  return new Promise(function (resolve, reject) {
    if (!JS_KEY) { reject(new Error('服务端未配置 AMAP_JS_KEY')); return; }
    if (window.AMap) { resolve(window.AMap); return; }
    if (JS_SEC) { window._AMapSecurityConfig = { securityJsCode: JS_SEC }; }
    var s = document.createElement('script');
    s.src = 'https://webapi.amap.com/maps?v=2.0&key=' + encodeURIComponent(JS_KEY);
    s.async = true;
    s.onload = function () {
      if (!window.AMap) { reject(new Error('高德地图未正确加载')); return; }
      window.AMap.plugin(['AMap.AutoComplete', 'AMap.Geocoder'], function () { resolve(window.AMap); });
    };
    s.onerror = function () { reject(new Error('地图脚本加载失败，请检查网络或 Key')); };
    document.head.appendChild(s);
  });
}

function hasInit() { return (INIT_LAT !== 0 || INIT_LNG !== 0); }

function showAddr(text) { $('addr').textContent = text; }

// 逆地理只是方便回填地址文本，失败也保留坐标，不打断选点
function fillAddress(xy) {
  if (!geocoder) return;
  geocoder.getAddress(xy, function (status, result) {
    var regeo = status === 'complete' && result.regeocode;
    if (!picked) return;
    picked.address = regeo ? regeo.formattedAddress : '';
    showAddr(describe());
  });
}

function describe() {
  if (!picked) return '尚未选择位置';
  var t = picked.lat.toFixed(6) + ', ' + picked.lng.toFixed(6);
  return picked.address ? (picked.address + '（' + t + '）') : t;
}

// 高德回调里的坐标形态不止一种（LngLat 对象 / 数组 / {lng,lat}），统一成 [lng, lat]
function toXY(v) {
  if (!v) return null;
  if (typeof v.getLng === 'function') return [v.getLng(), v.getLat()];
  if (Object.prototype.toString.call(v) === '[object Array]' && v.length === 2) return [Number(v[0]), Number(v[1])];
  if (typeof v.lng === 'number' && typeof v.lat === 'number') return [v.lng, v.lat];
  return null;
}

function place(target, skipRegeo) {
  var xy = toXY(target);
  if (!xy) return;
  var lng = xy[0], lat = xy[1];
  if (isNaN(lng) || isNaN(lat)) return;
  picked = { lng: Number(lng), lat: Number(lat), address: '' };
  if (!marker) {
    marker = new AMap.Marker({ position: [lng, lat], draggable: true, cursor: 'grab', map: map });
    marker.on('dragend', function (e) { place(e.lnglat, false); });
  } else {
    marker.setPosition([lng, lat]);
  }
  $('btnOk').disabled = false;
  showAddr(describe());
  if (!skipRegeo) fillAddress([lng, lat]);
}

function search() {
  var word = $('kw').value.trim();
  if (!word || !auto) return;
  auto.search(word, function (status, result) {
    var list = (status === 'complete' || status === 'no_result') ? (result.tips || []) : [];
    var box = $('tips');
    box.innerHTML = '';
    var valid = list.filter(function (t) { return toXY(t.location) !== null; }).slice(0, 8);
    valid.forEach(function (t) {
      var row = document.createElement('div');
      var nm = document.createElement('span'); nm.className = 'nm'; nm.textContent = t.name;
      var ad = document.createElement('span'); ad.className = 'ad'; ad.textContent = [t.district, t.address].filter(Boolean).join(' ');
      row.appendChild(nm); row.appendChild(ad);
      row.onclick = function () {
        var xy = toXY(t.location);
        if (!xy) return;
        box.classList.remove('show');
        map.setZoomAndCenter(16, xy);
        place(xy, false);
        // 搜索结果自带的名称先占位，逆地理回来后再补成完整地址
        if (picked && t.address) {
          picked.address = [t.district, t.address].filter(Boolean).join('');
          showAddr(describe());
        }
      };
      box.appendChild(row);
    });
    box.classList.toggle('show', valid.length > 0);
    if (!valid.length && status === 'complete') showAddr('没有找到带坐标的结果，试试更具体的名称');
  });
}

function submit() {
  if (!picked || submitting) return;
  submitting = true;
  $('btnOk').disabled = true;
  $('btnOk').textContent = '提交中…';
  // 直接回写到本页自己的 URL（GET 取页面、POST 交结果同一路径），
  // 这样即使后端挂在某个子路径下也不受影响
  fetch(window.location.href, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ latitude: picked.lat, longitude: picked.lng, address: picked.address })
  }).then(function (res) {
    return res.json().then(function (j) { return { ok: res.ok && j.code === 0, msg: j.message }; });
  }).then(function (r) {
    if (r.ok) {
      $('maskTtl').textContent = '已选好位置';
      $('maskMsg').textContent = '现在可以关闭这个页面，回到桌面端保存资料';
      $('mask').classList.remove('hide');
    } else {
      submitting = false;
      $('btnOk').disabled = false;
      $('btnOk').textContent = '确认选点';
      fail('提交失败', r.msg || '请重试，或回到桌面端重新发起选点');
    }
  }).catch(function (e) {
    submitting = false;
    $('btnOk').disabled = false;
    $('btnOk').textContent = '确认选点';
    fail('提交失败', e.message || '网络异常');
  });
}

if (ALREADY) {
  $('btnOk').disabled = true;
  // 已经提交过的链接不再加载地图，直接提示回到桌面端
  $('mask').classList.remove('hide');
} else {
  loadAmap().then(function (AMapNS) {
    AMap = AMapNS;
    var center = hasInit() ? [INIT_LNG, INIT_LAT] : DEFAULT_CENTER;
    map = new AMap.Map('map', { center: center, zoom: hasInit() ? 16 : 12, viewMode: '2D', resizeEnable: true });
    geocoder = new AMap.Geocoder({ city: '全国' });
    auto = new AMap.AutoComplete({ city: '全国' });
    map.on('click', function (e) { place(e.lnglat, false); });
    if (hasInit()) place([INIT_LNG, INIT_LAT], true);
    $('btnSearch').onclick = search;
    $('kw').addEventListener('keydown', function (e) { if (e.key === 'Enter') search(); });
    $('kw').addEventListener('input', function () { $('tips').classList.remove('show'); });
    $('btnOk').onclick = submit;
  }).catch(function (e) {
    fail('地图打不开', e.message || '请检查网络，或回到桌面端改用“按地址解析坐标”');
  });
}
</script>
</body>
</html>
`
