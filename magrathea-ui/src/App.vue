<template>
  <div class="app-container">
    <!-- Animated background -->
    <div class="bg-glow"></div>
    <div class="bg-grid"></div>
    <div class="bg-orbital"></div>

    <!-- Header -->
    <header class="header">
      <div class="header-brand">
        <span class="brand-icon">◈</span>
        <div class="brand-text">
          <h1>{{ $t('app.title') }}</h1>
          <span class="brand-sub">{{ $t('app.subtitle') }}</span>
        </div>
      </div>

      <!-- Language selector -->
      <div class="lang-switcher">
        <button
          v-for="loc in locales"
          :key="loc"
          :class="['lang-btn', { active: locale === loc }]"
          @click="switchLocale(loc)"
        >
          {{ localeNames[loc] }}
        </button>
      </div>
    </header>

    <!-- Main content -->
    <main class="main">
      <!-- Hero card -->
      <section class="hero-card">
        <div class="hero-icon">☁</div>
        <div class="hero-info">
          <h2>{{ $t('app.title') }}</h2>
          <p>{{ $t('app.subtitle') }}</p>
          <div class="hero-meta">
            <span class="meta-badge">S3 Compatible</span>
            <span class="meta-badge">WebFlux</span>
            <span class="meta-badge">Java 21</span>
          </div>
        </div>
      </section>

      <!-- Dashboard grid -->
      <section class="dashboard">
        <div class="card" @click="section = 'buckets'">
          <div class="card-icon">🗂</div>
          <div class="card-label">{{ $t('nav.buckets') }}</div>
          <div class="card-desc">Manage your S3 buckets</div>
        </div>
        <div class="card" @click="section = 'objects'">
          <div class="card-icon">📦</div>
          <div class="card-label">{{ $t('nav.objects') }}</div>
          <div class="card-desc">Browse and manage objects</div>
        </div>
        <div class="card" @click="section = 'health'">
          <div class="card-icon">❤️</div>
          <div class="card-label">{{ $t('nav.health') }}</div>
          <div class="card-desc">System status and metrics</div>
        </div>
        <div class="card">
          <div class="card-icon">⚙</div>
          <div class="card-label">{{ $t('nav.settings') }}</div>
          <div class="card-desc">Configure preferences</div>
        </div>
      </section>

      <!-- Terminal log -->
      <section class="terminal">
        <div class="terminal-header">
          <span class="terminal-dot red"></span>
          <span class="terminal-dot yellow"></span>
          <span class="terminal-dot green"></span>
          <span class="terminal-title">magrathea-console</span>
        </div>
        <div class="terminal-body">
          <div class="terminal-line">
            <span class="prompt">$</span>
            <span class="cmd">server start — port 8080</span>
            <span class="status ok">✓</span>
          </div>
          <div class="terminal-line">
            <span class="prompt">$</span>
            <span class="cmd">admin-api — port 8081</span>
            <span class="status ok">✓</span>
          </div>
          <div class="terminal-line">
            <span class="prompt">$</span>
            <span class="cmd">frontend — port 5173</span>
            <span class="status ok">✓</span>
          </div>
          <div class="terminal-line blink">
            <span class="prompt">$</span>
            <span class="cmd cursor">_</span>
          </div>
        </div>
      </section>
    </main>

    <!-- Footer -->
    <footer class="footer">
      <p>{{ $t('app.footer') }}</p>
      <div class="footer-langs">
        <span v-for="loc in locales" :key="loc" class="footer-lang" @click="switchLocale(loc)">
          {{ localeNames[loc] }}
        </span>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { locales, localeNames } from './i18n/index.js'

const { locale } = useI18n()
const section = ref('buckets')

function switchLocale(loc) {
  locale.value = loc
  localStorage.setItem('magrathea-lang', loc)
}

// Restore saved language
const saved = localStorage.getItem('magrathea-lang')
if (saved) locale.value = saved
</script>

<style scoped>
.app-container {
  position: relative;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  padding: 0 2rem;
}

/* ── Animated Background ── */
.bg-glow {
  position: fixed;
  inset: 0;
  z-index: -3;
  background:
    radial-gradient(ellipse 80% 60% at 20% 20%, rgba(124, 92, 255, 0.08) 0%, transparent 60%),
    radial-gradient(ellipse 60% 50% at 80% 80%, rgba(54, 214, 184, 0.06) 0%, transparent 60%),
    radial-gradient(ellipse 50% 40% at 50% 50%, rgba(255, 122, 92, 0.04) 0%, transparent 60%);
}

.bg-grid {
  position: fixed;
  inset: 0;
  z-index: -2;
  background-image:
    linear-gradient(rgba(255,255,255,0.02) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255,255,255,0.02) 1px, transparent 1px);
  background-size: 60px 60px;
}

.bg-orbital {
  position: fixed;
  inset: 0;
  z-index: -1;
  overflow: hidden;
}

.bg-orbital::before {
  content: '';
  position: absolute;
  width: 600px;
  height: 600px;
  top: -200px;
  right: -200px;
  border: 1px solid rgba(124, 92, 255, 0.1);
  border-radius: 50%;
  animation: orbital 30s linear infinite;
}

.bg-orbital::after {
  content: '';
  position: absolute;
  width: 400px;
  height: 400px;
  bottom: -100px;
  left: -100px;
  border: 1px solid rgba(54, 214, 184, 0.08);
  border-radius: 50%;
  animation: orbital 25s linear infinite reverse;
}

@keyframes orbital {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

/* ── Header ── */
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1.5rem 0;
  border-bottom: 1px solid var(--border-glass);
  position: relative;
  z-index: 1;
}

.header-brand {
  display: flex;
  align-items: center;
  gap: 1rem;
}

.brand-icon {
  font-size: 2rem;
  color: var(--accent-teal);
  text-shadow: 0 0 20px var(--accent-glow);
}

.brand-text h1 {
  font-size: 1.4rem;
  font-weight: 700;
  background: linear-gradient(135deg, var(--accent) 0%, var(--accent-teal) 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.brand-sub {
  font-size: 0.75rem;
  color: var(--text-secondary);
  font-weight: 400;
}

/* ── Language Switcher ── */
.lang-switcher {
  display: flex;
  gap: 0.25rem;
  background: var(--bg-card);
  padding: 0.25rem;
  border-radius: 8px;
  border: 1px solid var(--border-glass);
}

.lang-btn {
  padding: 0.35rem 0.7rem;
  border-radius: 6px;
  font-size: 0.75rem;
  font-weight: 500;
  background: transparent;
  color: var(--text-secondary);
  transition: all 0.2s;
}

.lang-btn:hover {
  background: var(--bg-card-hover);
  color: var(--text-primary);
}

.lang-btn.active {
  background: var(--accent);
  color: #fff;
  box-shadow: 0 0 12px var(--accent-glow);
}

/* ── Hero Card ── */
.hero-card {
  display: flex;
  align-items: center;
  gap: 1.5rem;
  padding: 2rem;
  margin: 2rem 0;
  background: var(--bg-card);
  border: 1px solid var(--border-glass);
  border-radius: 16px;
  backdrop-filter: blur(12px);
  transition: all 0.3s;
}

.hero-card:hover {
  border-color: var(--border-glass-hover);
  background: var(--bg-card-hover);
}

.hero-icon {
  font-size: 3.5rem;
  filter: drop-shadow(0 0 20px rgba(124, 92, 255, 0.3));
}

.hero-info h2 {
  font-size: 1.6rem;
  font-weight: 700;
  margin-bottom: 0.25rem;
}

.hero-info p {
  color: var(--text-secondary);
  font-size: 0.95rem;
  margin-bottom: 0.75rem;
}

.hero-meta {
  display: flex;
  gap: 0.5rem;
}

.meta-badge {
  padding: 0.2rem 0.6rem;
  border-radius: 999px;
  font-size: 0.7rem;
  font-weight: 500;
  background: rgba(124, 92, 255, 0.15);
  color: var(--accent);
  border: 1px solid rgba(124, 92, 255, 0.25);
}

/* ── Dashboard Grid ── */
.dashboard {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 1rem;
  margin-bottom: 2rem;
}

.card {
  padding: 1.5rem;
  background: var(--bg-card);
  border: 1px solid var(--border-glass);
  border-radius: 12px;
  backdrop-filter: blur(12px);
  cursor: pointer;
  transition: all 0.3s;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 0.5rem;
}

.card:hover {
  border-color: var(--accent);
  background: var(--bg-card-hover);
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(124, 92, 255, 0.1);
}

.card-icon {
  font-size: 2rem;
}

.card-label {
  font-size: 1.1rem;
  font-weight: 600;
}

.card-desc {
  font-size: 0.8rem;
  color: var(--text-muted);
}

/* ── Terminal ── */
.terminal {
  background: rgba(0, 0, 0, 0.6);
  border: 1px solid var(--border-glass);
  border-radius: 12px;
  overflow: hidden;
  margin-bottom: 2rem;
  font-family: var(--font-mono);
}

.terminal-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem 1rem;
  background: rgba(0, 0, 0, 0.4);
  border-bottom: 1px solid var(--border-glass);
}

.terminal-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.terminal-dot.red { background: #ff5c5c; }
.terminal-dot.yellow { background: #ffbe5c; }
.terminal-dot.green { background: #5cff7a; }

.terminal-title {
  margin-left: auto;
  font-size: 0.75rem;
  color: var(--text-muted);
}

.terminal-body {
  padding: 1rem;
}

.terminal-line {
  display: flex;
  gap: 0.75rem;
  padding: 0.2rem 0;
  font-size: 0.85rem;
}

.prompt { color: var(--accent-teal); }
.cmd { color: var(--text-secondary); }
.status.ok { color: var(--accent-green); margin-left: auto; }
.status.fail { color: var(--danger); margin-left: auto; }

.blink .cursor {
  animation: blink 1s step-end infinite;
}

@keyframes blink {
  50% { opacity: 0; }
}

/* ── Footer ── */
.footer {
  margin-top: auto;
  padding: 1.5rem 0;
  border-top: 1px solid var(--border-glass);
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 0.8rem;
  color: var(--text-muted);
}

.footer-langs {
  display: flex;
  gap: 0.75rem;
}

.footer-lang {
  cursor: pointer;
  transition: color 0.2s;
}

.footer-lang:hover {
  color: var(--accent);
}
</style>
