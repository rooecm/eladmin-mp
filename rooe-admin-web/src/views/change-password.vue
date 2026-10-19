<template>
  <div class="password-page">
    <el-card>
      <h2>修改密码</h2>
      <p>修改成功后请重新登录。</p>
      <el-form @submit.native.prevent="submit">
        <el-form-item label="当前密码">
          <el-input v-model="oldPassword" autocomplete="current-password" type="password" />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="newPassword" autocomplete="new-password" type="password" />
        </el-form-item>
        <el-form-item label="确认密码">
          <el-input v-model="confirmation" autocomplete="new-password" type="password" />
        </el-form-item>
        <el-button :loading="loading" native-type="submit" type="primary">保存密码</el-button>
        <el-button @click="exit">退出登录</el-button>
      </el-form>
    </el-card>
  </div>
</template>

<script>
import { changePassword } from '@/api/login'

export default {
  data: () => ({ oldPassword: '', newPassword: '', confirmation: '', loading: false }),
  methods: {
    async submit() {
      if (this.newPassword.length < 8 || this.newPassword !== this.confirmation || new TextEncoder().encode(this.newPassword).length > 72) {
        this.$message.error('新密码至少 8 位且不超过 72 字节，两次输入须一致')
        return
      }
      this.loading = true
      try {
        await changePassword({ oldPassword: this.oldPassword, newPassword: this.newPassword })
        await this.$store.dispatch('ClearAuth')
        await this.$router.replace('/login')
      } finally {
        this.loading = false
      }
    },
    async exit() {
      try {
        await this.$store.dispatch('LogOut')
      } finally {
        this.$router.replace('/login')
      }
    }
  }
}
</script>

<style scoped>
.password-page {
  max-width: 440px;
  margin: 10vh auto;
}
</style>
