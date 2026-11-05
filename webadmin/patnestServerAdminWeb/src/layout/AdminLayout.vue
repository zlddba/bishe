<template>
  <div class="admin-app">
    <aside class="sidebar">
      <div class="side-logo">
        <span class="brand-badge">PN</span>
        <span>宠栖后台</span>
      </div>
      <nav class="nav">
        <div class="nav-cap">总览</div>
        <div class="nav-item" :class="{active: route.path==='/dashboard'}" @click="go('/dashboard')">
          <el-icon><Odometer /></el-icon>经营看板
        </div>
        <div class="nav-cap">业务</div>
        <div class="nav-item" :class="{active: route.path==='/sitters'}" @click="go('/sitters')">
          <el-icon><Shop /></el-icon>机构管理
        </div>
        <div class="nav-item" :class="{active: route.path==='/orders'}" @click="go('/orders')">
          <el-icon><Document /></el-icon>订单管理
        </div>
        <div class="nav-item" :class="{active: route.path==='/pets'}" @click="go('/pets')">
          <el-icon><Star /></el-icon>宠物管理
        </div>
        <div class="nav-item" :class="{active: route.path==='/dynamics'}" @click="go('/dynamics')">
          <el-icon><Picture /></el-icon>寄养动态
        </div>
        <div class="nav-item" :class="{active: route.path==='/live'}" @click="go('/live')">
          <el-icon><VideoCamera /></el-icon>直播管理
        </div>
        <div class="nav-item" :class="{active: route.path==='/reviews'}" @click="go('/reviews')">
          <el-icon><ChatDotRound /></el-icon>评价管理
        </div>
        <div class="nav-cap">运营</div>
        <div class="nav-item" :class="{active: route.path==='/coupons'}" @click="go('/coupons')">
          <el-icon><PriceTag /></el-icon>优惠券
        </div>
        <div class="nav-item" :class="{active: route.path==='/notifications'}" @click="go('/notifications')">
          <el-icon><Bell /></el-icon>通知公告
        </div>
        <div class="nav-item" :class="{active: route.path==='/templates'}" @click="go('/templates')">
          <el-icon><MessageBox /></el-icon>消息模板
        </div>
        <div class="nav-item" :class="{active: route.path==='/push-tasks'}" @click="go('/push-tasks')">
          <el-icon><Promotion /></el-icon>推送任务
        </div>
        <div class="nav-cap">服务</div>
        <div class="nav-item" :class="{active: route.path==='/aftersales'}" @click="go('/aftersales')">
          <el-icon><Service /></el-icon>售后服务
        </div>
        <div class="nav-item" :class="{active: route.path==='/settlements'}" @click="go('/settlements')">
          <el-icon><Wallet /></el-icon>机构结算
        </div>
        <div class="nav-item" :class="{active: route.path==='/wallets'}" @click="go('/wallets')">
          <el-icon><Wallet /></el-icon>钱包管理
        </div>
        <div class="nav-cap">系统</div>
        <div class="nav-item" :class="{active: route.path==='/users'}" @click="go('/users')">
          <el-icon><User /></el-icon>用户管理
        </div>
        <div class="nav-item" :class="{active: route.path==='/permissions'}" @click="go('/permissions')">
          <el-icon><Lock /></el-icon>权限配置
        </div>
        <div class="nav-item" :class="{active: route.path==='/audit'}" @click="go('/audit')">
          <el-icon><Notebook /></el-icon>审计日志
        </div>
        <div class="nav-item" :class="{active: route.path==='/updates'}" @click="go('/updates')">
          <el-icon><UploadFilled /></el-icon>系统更新
        </div>
      </nav>
      <div class="side-foot">
        <div class="side-user">
          <span class="avatar">{{ avatarText }}</span>
          <div>
            <div style="font-size:13px;font-weight:500;color:var(--ink)">{{ auth.username }}</div>
            <div style="font-size:12px;color:var(--ink3)">管理员</div>
          </div>
          <el-icon style="margin-left:auto;cursor:pointer;color:var(--ink3)" @click="handleLogout"><SwitchButton /></el-icon>
        </div>
        <el-tooltip placement="top" :show-after="300">
          <template #content>
            <div style="font-size:12px;line-height:1.7">
              <div>运行版本：v{{ displayVersion }}{{ backendSlot ? `（${backendSlot}）` : '' }}</div>
              <div>页面构建：v{{ version }} · {{ commit }}</div>
              <div v-if="backendBuildTime && backendBuildTime !== 'unknown'">构建时间：{{ backendBuildTime }}</div>
            </div>
          </template>
          <div class="side-ver">v{{ displayVersion }} · {{ displayCommit }}</div>
        </el-tooltip>
      </div>
    </aside>

    <div class="main-area">
      <header class="top-bar">
        <div class="top-title">{{ pageTitle }}</div>
        <div class="top-crumb">宠栖后台 / {{ pageTitle }}</div>
        <div class="top-spacer"></div>
        <el-tooltip :content="hasUpdate ? '检测到新版本，点击刷新页面' : '检查管理面板更新'" placement="bottom">
          <button type="button" class="ver-chip" :class="{ 'has-update': hasUpdate }" @click="onVersionClick">
            <el-icon><Refresh v-if="hasUpdate" /><CircleCheck v-else /></el-icon>
            <span>{{ hasUpdate ? '发现新版本' : '已是最新' }}</span>
          </button>
        </el-tooltip>
        <div class="top-search">
          <el-icon><Search /></el-icon>
          <input placeholder="搜索订单号、机构、用户..." />
        </div>
      </header>
      <main class="content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useAppVersion } from '@/composables/useAppVersion'
import { Odometer, Shop, Document, Star, Picture, ChatDotRound, User, Notebook, UploadFilled, SwitchButton, Search, Bell, Refresh, CircleCheck, PriceTag, MessageBox, Promotion, Service, Wallet, Lock, VideoCamera } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const { version, commit, backendSlot, backendBuildTime, displayVersion, displayCommit, hasUpdate, checkVersion, applyUpdate } = useAppVersion()

const pageTitle = computed(() => route.meta?.title || '宠栖后台')
const avatarText = computed(() => (auth.username || 'A').charAt(0).toUpperCase())

function go(path) { router.push(path) }

function handleLogout() {
  auth.logout()
  router.push('/login')
}

function onVersionClick() {
  if (hasUpdate.value) {
    applyUpdate()
  } else {
    checkVersion()
  }
}
</script>

<style scoped>
.admin-app { display: flex; width: 100%; height: 100vh; overflow: hidden; }

.sidebar {
  width: 228px;
  background: var(--panel);
  border-right: 1px solid var(--line);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}
.side-logo {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px 18px;
  font-weight: 600;
  font-size: 15px;
  border-bottom: 1px solid var(--line2);
}
.brand-badge {
  width: 30px; height: 30px;
  border-radius: 7px;
  background: var(--brand);
  color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-weight: 700; font-size: 12px;
}
.nav { flex: 1; padding: 12px 10px; overflow-y: auto; }
.nav-cap { font-size: 11px; color: var(--ink3); letter-spacing: 0.06em; padding: 14px 10px 6px; }
.nav-item {
  display: flex; align-items: center; gap: 11px;
  padding: 9px 12px; border-radius: 6px;
  color: var(--ink2); font-size: 14px;
  cursor: pointer; user-select: none;
  transition: background 0.15s, color 0.15s;
}
.nav-item:hover { background: var(--line2); }
.nav-item.active { background: var(--brand-soft); color: var(--brand-ink); font-weight: 500; }
.side-foot { border-top: 1px solid var(--line2); padding: 12px 14px; }
.side-ver { margin-top: 8px; font-size: 11px; color: var(--ink3); text-align: center; }
.side-user { display: flex; align-items: center; gap: 10px; }
.avatar {
  width: 30px; height: 30px; border-radius: 50%;
  background: var(--brand-soft); color: var(--brand-ink);
  display: flex; align-items: center; justify-content: center;
  font-weight: 600; font-size: 13px;
}

.main-area { flex: 1; min-width: 0; display: flex; flex-direction: column; overflow: hidden; }
.top-bar {
  background: var(--panel);
  border-bottom: 1px solid var(--line);
  display: flex; align-items: center; gap: 16px;
  padding: 0 24px; height: 60px;
  flex-shrink: 0;
}
.top-title { font-size: 17px; font-weight: 600; color: var(--ink); }
.top-crumb { font-size: 12.5px; color: var(--ink3); }
.top-spacer { flex: 1; }
.top-search {
  display: flex; align-items: center; gap: 8px;
  border: 1px solid var(--line); border-radius: var(--radius);
  padding: 6px 12px; color: var(--ink3); font-size: 13.5px; width: 240px;
}
.top-search input { border: none; outline: none; font-size: 13.5px; width: 180px; color: var(--ink); background: transparent; }
.ver-chip {
  display: inline-flex; align-items: center; gap: 6px;
  height: 30px; padding: 0 10px;
  border: 1px solid var(--line); border-radius: var(--radius);
  background: var(--panel); color: var(--ink3);
  font-size: 12.5px; cursor: pointer;
  transition: border-color .15s, color .15s, background .15s;
}
.ver-chip:hover { border-color: var(--brand); color: var(--brand); }
.ver-chip.has-update { border-color: var(--brand); background: var(--brand-soft); color: var(--brand-ink); font-weight: 500; }
.content { padding: 24px; flex: 1; overflow-y: auto; }
</style>
