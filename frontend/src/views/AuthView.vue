<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter, RouterLink } from 'vue-router'
import { ElMessage } from 'element-plus'
import BrandMark from '@/components/BrandMark.vue'
import AuthArtwork from '@/components/AuthArtwork.vue'
import AuthPasswordInput from '@/components/AuthPasswordInput.vue'
import AppIcon from '@/components/AppIcon.vue'
import { useAuthStore } from '@/stores/auth'
import { registerApi } from '@/api/auth'
function safeRedirect(value) {
  return typeof value === 'string' && /^\/drive(?:\/|\?|#|$)/.test(value) ? value : '/drive'
}
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const registering = computed(() => route.name === 'register')
const formRef = ref()
const submitting = ref(false)
const errorMessage = ref('')
const form = reactive({
  username: '',
  nickname: '',
  password: '',
  confirmPassword: '',
})
const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    {
      pattern: /^[a-zA-Z0-9_]{3,32}$/,
      message: '3～32 位字母、数字或下划线',
      trigger: 'blur',
    },
  ],
  nickname: [{ max: 32, message: '昵称最多 32 个字符', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    {
      pattern: /^[\x21-\x7E]{8,64}$/,
      message: '8～64 位字母、数字或英文符号，不含空格',
      trigger: 'blur',
    },
  ],
  confirmPassword: [
    {
      validator: (_rule, value, done) =>
        done(value && value === form.password ? undefined : new Error('两次输入的密码不一致')),
      trigger: 'blur',
    },
  ],
}
watch(
  () => route.fullPath,
  () => {
    errorMessage.value = route.query.reason === 'expired' ? '登录状态已失效，请重新登录' : ''
    form.username = typeof route.query.username === 'string' ? route.query.username : ''
    form.nickname = ''
    form.password = ''
    form.confirmPassword = ''
    formRef.value?.clearValidate()
  },
  { immediate: true },
)
watch(
  () => form.password,
  () => {
    if (registering.value && form.confirmPassword)
      void formRef.value?.validateField('confirmPassword').catch(() => {})
  },
)
async function submit() {
  if (submitting.value) return
  submitting.value = true
  errorMessage.value = ''
  try {
    if (!(await formRef.value.validate().catch(() => false))) return
    if (registering.value) {
      const user = await registerApi({
        username: form.username,
        password: form.password,
        nickname: form.nickname || null,
      })
      form.password = ''
      form.confirmPassword = ''
      ElMessage.success('注册成功，请登录')
      await router.replace({
        name: 'login',
        query: {
          username: user.username,
          redirect: safeRedirect(route.query.redirect),
        },
      })
    } else {
      await auth.login({ username: form.username, password: form.password })
      form.password = ''
      await router.replace(safeRedirect(route.query.redirect))
    }
  } catch (error) {
    errorMessage.value = error.message || '请求失败，请重试'
  } finally {
    submitting.value = false
  }
}
</script>
<template>
  <div class="auth-page">
    <header class="auth-header">
      <RouterLink
        to="/login"
        aria-label="个人云盘首页"
        :aria-disabled="submitting"
        :tabindex="submitting ? -1 : undefined"
        @click.capture="submitting && $event.preventDefault()"
      >
        <BrandMark />
      </RouterLink>
      <span class="auth-top-note">让文件，回到你的掌握中</span>
    </header>
    <main class="auth-main">
      <AuthArtwork />
      <section class="auth-form-panel">
        <div class="auth-form-inner">
          <span class="eyebrow">
            {{ registering ? '开启你的文件空间' : '欢迎回来' }}
          </span>
          <h1>{{ registering ? '创建账号' : '登录个人云盘' }}</h1>
          <p class="muted auth-subtitle">
            {{
              registering ? '为重要的文件，找一个自己的家。' : '你的文档、照片和灵感，都在这里。'
            }}
          </p>
          <el-alert
            v-if="errorMessage"
            class="form-alert"
            :title="errorMessage"
            type="error"
            :closable="false"
            show-icon
          />
          <el-form
            ref="formRef"
            :model="form"
            :rules="rules"
            label-position="top"
            :aria-busy="submitting"
            @submit.prevent="submit"
          >
            <el-form-item label="用户名" prop="username">
              <el-input
                v-model.trim="form.username"
                autocomplete="username"
                name="username"
                placeholder="请输入用户名"
                :maxlength="32"
                size="large"
                :disabled="submitting"
              >
                <template #prefix><AppIcon name="user" :size="18" /></template>
              </el-input>
            </el-form-item>
            <el-form-item v-if="registering" label="昵称（选填）" prop="nickname">
              <el-input
                v-model.trim="form.nickname"
                autocomplete="nickname"
                placeholder="希望我们怎么称呼你"
                :maxlength="32"
                size="large"
                :disabled="submitting"
              />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <AuthPasswordInput
                v-model="form.password"
                name="password"
                :autocomplete="registering ? 'new-password' : 'current-password'"
                :placeholder="registering ? '设置 8～64 位密码' : '请输入密码'"
                :disabled="submitting"
              />
            </el-form-item>
            <el-form-item v-if="registering" label="确认密码" prop="confirmPassword">
              <AuthPasswordInput
                v-model="form.confirmPassword"
                name="confirmPassword"
                label="确认密码"
                autocomplete="new-password"
                placeholder="再次输入密码"
                :disabled="submitting"
              />
            </el-form-item>
            <el-button
              class="auth-submit"
              type="primary"
              native-type="submit"
              size="large"
              :loading="submitting"
              :disabled="submitting"
            >
              {{ registering ? '创建账号' : '登录' }}
              <AppIcon v-if="!submitting" name="arrow" :size="18" />
            </el-button>
          </el-form>
          <p class="auth-switch">
            {{ registering ? '已有账号？' : '还没有账号？' }}
            <RouterLink
              :aria-disabled="submitting"
              :tabindex="submitting ? -1 : undefined"
              @click.capture="submitting && $event.preventDefault()"
              :to="{
                name: registering ? 'login' : 'register',
                query: { redirect: safeRedirect(route.query.redirect) },
              }"
            >
              {{ registering ? '返回登录' : '创建账号' }}
            </RouterLink>
          </p>
          <p class="auth-footnote">
            <AppIcon name="lock" :size="14" />
            使用个人账号，进入你的文件空间。
          </p>
        </div>
      </section>
    </main>
    <footer class="auth-footer">
      <span>AIRUIKE DRIVE · 个人文件空间</span>
      <span>每一份重要，都妥善安放</span>
    </footer>
  </div>
</template>
